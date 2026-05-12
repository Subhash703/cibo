import json as json_lib
from contextlib import asynccontextmanager
from datetime import date
from pathlib import Path

from dotenv import load_dotenv
from fastapi import Depends, FastAPI, File, Form, Header, HTTPException, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from google.genai import errors as genai_errors
from sqlalchemy.orm import Session

# Load apps/backend/.env into the process environment before any module reads
# os.environ. Lets the dev workflow be `uvicorn foodlens_backend.main:app …`
# instead of inlining secrets on the command line. .env is gitignored.
_dotenv_path = Path(__file__).resolve().parents[2] / ".env"
if _dotenv_path.exists():
    load_dotenv(_dotenv_path)

from .analyze import analyze_items
from .auth import (
    create_access_token,
    current_user,
    hash_password,
    optional_current_user,
    verify_password,
)
from .db import get_db, init_db
from .models import MealLog, User
from .schemas import (
    AnalyzeRequest,
    AnalyzeResponse,
    AuthResponse,
    LoginRequest,
    MealLogRequest,
    ProfileUpdate,
    RegisterRequest,
    TodaySummary,
    UserPublic,
)
from .tdee import VALID_ACTIVITY_LEVELS, VALID_SEXES, compute_tdee
from .vision import analyze_with_vision


def _to_user_public(user: User) -> UserPublic:
    """Builds a UserPublic from a User row, including the computed TDEE."""
    return UserPublic(
        email=user.email,
        name=user.name,
        picture=user.picture,
        daily_kcal_target=user.daily_kcal_target,
        birth_year=user.birth_year,
        sex=user.sex,
        weight_kg=user.weight_kg,
        height_cm=user.height_cm,
        activity_level=user.activity_level,
        suggested_kcal_target=compute_tdee(user),
    )


@asynccontextmanager
async def lifespan(app: FastAPI):
    init_db()
    yield


app = FastAPI(title="Cibo API", version="0.5.0", lifespan=lifespan)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

_MAX_IMAGE_BYTES = 4 * 1024 * 1024  # 4 MB


def _today_summary(db: Session, user: User) -> TodaySummary:
    """Compute the user's daily totals from today's MealLog rows."""
    logs = (
        db.query(MealLog)
        .filter(MealLog.user_id == user.id, MealLog.log_date == date.today())
        .all()
    )
    consumed = sum(log.kcal for log in logs)
    return TodaySummary(
        daily_kcal_target=user.daily_kcal_target,
        consumed_kcal=consumed,
        remaining_kcal=max(0, user.daily_kcal_target - consumed),
        consumed_protein_g=sum(log.protein_g for log in logs),
        consumed_fat_g=sum(log.fat_g for log in logs),
        consumed_carbs_g=sum(log.carbs_g for log in logs),
        log_count=len(logs),
    )


@app.get("/healthz")
def healthz() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/analyze", response_model=AnalyzeResponse)
def analyze(request: AnalyzeRequest) -> AnalyzeResponse:
    """Legacy text-based analyzer (OCR strings → DB lookup)."""
    core = analyze_items(request)
    return AnalyzeResponse(**core.model_dump())


@app.post("/analyze-vision", response_model=AnalyzeResponse)
async def analyze_vision(
    image: UploadFile = File(...),
    region_hint: str | None = Form(default=None),
    authorization: str = Header(default=""),
    db: Session = Depends(get_db),
) -> AnalyzeResponse:
    """Primary analyzer: send the screenshot to Gemini 2.5 Flash. If the
    request includes a valid Authorization header, the response also
    includes today's running totals and the user's daily kcal goal."""
    data = await image.read()
    if not data:
        raise HTTPException(status_code=400, detail="Empty image upload.")
    if len(data) > _MAX_IMAGE_BYTES:
        raise HTTPException(
            status_code=413,
            detail=f"Image too large ({len(data)} bytes). Max {_MAX_IMAGE_BYTES}.",
        )
    mime = image.content_type or "image/jpeg"
    try:
        core = await analyze_with_vision(data, mime_type=mime)
    except RuntimeError as e:
        # Configuration / parse failures (missing API key, malformed model output).
        raise HTTPException(status_code=500, detail=str(e)) from e
    except genai_errors.APIError as e:
        # Map Gemini's transient overload signals to a clean 503 so the
        # Android bubble can show a "try again in a moment" message instead
        # of the raw upstream traceback. 429 (rate limit) and 503 (model
        # overloaded) are both expected during peak hours on free tier.
        upstream = getattr(e, "code", 500)
        if upstream in (429, 503):
            raise HTTPException(
                status_code=503,
                detail="The AI service is briefly busy. Please try again in a few seconds.",
            ) from e
        raise HTTPException(
            status_code=502,
            detail=f"AI service error ({upstream}).",
        ) from e

    user = optional_current_user(authorization=authorization, db=db)
    summary = _today_summary(db, user) if user else None
    return AnalyzeResponse(**core.model_dump(), daily_summary=summary)


# --- Auth + profile ---------------------------------------------------------


@app.post("/auth/register", response_model=AuthResponse, status_code=201)
def register(req: RegisterRequest, db: Session = Depends(get_db)) -> AuthResponse:
    email = req.email.strip().lower()
    if "@" not in email or "." not in email:
        raise HTTPException(status_code=400, detail="Invalid email address.")
    existing = db.query(User).filter_by(email=email).first()
    if existing is not None:
        raise HTTPException(status_code=409, detail="Email already registered.")
    user = User(
        email=email,
        name=req.name.strip() if req.name else None,
        password_hash=hash_password(req.password),
    )
    db.add(user)
    db.commit()
    db.refresh(user)
    return AuthResponse(
        token=create_access_token(user.id),
        user=_to_user_public(user),
    )


@app.post("/auth/login", response_model=AuthResponse)
def login(req: LoginRequest, db: Session = Depends(get_db)) -> AuthResponse:
    email = req.email.strip().lower()
    user = db.query(User).filter_by(email=email).first()
    if user is None or user.password_hash is None:
        raise HTTPException(status_code=401, detail="Invalid email or password.")
    if not verify_password(req.password, user.password_hash):
        raise HTTPException(status_code=401, detail="Invalid email or password.")
    return AuthResponse(
        token=create_access_token(user.id),
        user=_to_user_public(user),
    )


@app.get("/me", response_model=UserPublic)
def get_me(user: User = Depends(current_user)) -> UserPublic:
    return _to_user_public(user)


@app.patch("/me", response_model=UserPublic)
def update_me(
    req: ProfileUpdate,
    user: User = Depends(current_user),
    db: Session = Depends(get_db),
) -> UserPublic:
    if req.daily_kcal_target is not None:
        user.daily_kcal_target = req.daily_kcal_target
    if req.birth_year is not None:
        user.birth_year = req.birth_year
    if req.sex is not None:
        sex = req.sex.strip().lower()
        if sex not in VALID_SEXES:
            raise HTTPException(
                status_code=422,
                detail=f"sex must be one of {sorted(VALID_SEXES)}",
            )
        user.sex = sex
    if req.weight_kg is not None:
        user.weight_kg = req.weight_kg
    if req.height_cm is not None:
        user.height_cm = req.height_cm
    if req.activity_level is not None:
        level = req.activity_level.strip().lower()
        if level not in VALID_ACTIVITY_LEVELS:
            raise HTTPException(
                status_code=422,
                detail=f"activity_level must be one of {sorted(VALID_ACTIVITY_LEVELS)}",
            )
        user.activity_level = level
    db.commit()
    db.refresh(user)
    return _to_user_public(user)


# --- Meal logging -----------------------------------------------------------


@app.get("/me/today", response_model=TodaySummary)
def me_today(
    user: User = Depends(current_user),
    db: Session = Depends(get_db),
) -> TodaySummary:
    return _today_summary(db, user)


@app.post("/meal-logs", response_model=TodaySummary)
def log_meal(
    req: MealLogRequest,
    user: User = Depends(current_user),
    db: Session = Depends(get_db),
) -> TodaySummary:
    """Record a confirmed order — one row per 'Yes, I'm ordering' tap.
    Returns the updated daily summary so the client can refresh the bubble."""
    log = MealLog(
        user_id=user.id,
        log_date=date.today(),
        kcal=req.macros.kcal,
        protein_g=req.macros.protein_g,
        fat_g=req.macros.fat_g,
        carbs_g=req.macros.carbs_g,
        health_score=req.health_score,
        items_json=json_lib.dumps([item.model_dump() for item in req.items]),
    )
    db.add(log)
    db.commit()
    return _today_summary(db, user)
