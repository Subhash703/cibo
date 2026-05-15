import json as json_lib
import logging
from contextlib import asynccontextmanager
from datetime import date, datetime, timedelta
from pathlib import Path

from dotenv import load_dotenv
from fastapi import Depends, FastAPI, File, Form, Header, HTTPException, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import Response
from google.genai import errors as genai_errors
from sqlalchemy.orm import Session

logger = logging.getLogger("foodlens.backend")
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")

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
from .insights import VALID_GOALS, generate_goal_insight
from .models import MealLog, User
from .schemas import (
    AnalyzeRequest,
    AnalyzeResponse,
    AuthResponse,
    DailySummaryPoint,
    HistoryResponse,
    LoginRequest,
    MatchedItem,
    MealLogPublic,
    MealLogRequest,
    PlateAnalyzeResponse,
    ProfileUpdate,
    RegisterRequest,
    TodaySummary,
    UserPublic,
)
from .tdee import VALID_ACTIVITY_LEVELS, VALID_SEXES, compute_tdee
from .vision import analyze_plate_with_vision, analyze_with_vision


def _to_user_public(user: User) -> UserPublic:
    """Builds a UserPublic from a User row, including the computed TDEE."""
    # If the user has uploaded an avatar, point `picture` at our public
    # avatar endpoint with a cache-busting `?v=` so clients re-fetch when
    # they change their photo. The path is relative — clients prepend
    # their configured API base URL.
    picture: str | None = user.picture
    if user.avatar_bytes is not None:
        ts = int(user.avatar_updated_at.timestamp()) if user.avatar_updated_at else 0
        picture = f"/avatars/{user.id}?v={ts}"
    return UserPublic(
        email=user.email,
        name=user.name,
        picture=picture,
        daily_kcal_target=user.daily_kcal_target,
        birth_year=user.birth_year,
        sex=user.sex,
        weight_kg=user.weight_kg,
        height_cm=user.height_cm,
        activity_level=user.activity_level,
        suggested_kcal_target=compute_tdee(user),
        goal=user.goal,
        goal_insight=user.goal_insight,
        has_avatar=user.avatar_bytes is not None,
        scans_used=user.scans_used or 0,
        scans_limit=FREE_SCAN_LIMIT,
    )


# Profile fields that, when changed, should trigger a fresh AI insight.
_INSIGHT_TRIGGER_FIELDS = (
    "goal", "daily_kcal_target", "weight_kg", "height_cm",
    "birth_year", "sex", "activity_level",
)


async def _maybe_refresh_insight(
    user: User,
    db: Session,
    changed: set[str],
) -> None:
    """If a profile change touched anything that affects the AI insight,
    regenerate it. Skips silently when Gemini is unavailable — the cached
    text (or None) stays put."""
    if not user.goal:
        return
    if not (changed & set(_INSIGHT_TRIGGER_FIELDS)):
        return
    text = await generate_goal_insight(user)
    if text:
        user.goal_insight = text
        user.goal_insight_at = datetime.utcnow()
        db.commit()
        db.refresh(user)


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

# Free-tier cap. Anonymous /analyze-vision is excluded — the limit only
# bites when the user is signed in (we have no other handle on them).
FREE_SCAN_LIMIT = 16
_PAYWALL_DETAIL = (
    "You've used all {limit} free scans. Cibo Premium is coming soon — "
    "we'll let you know the moment unlimited scans land."
)


def _enforce_scan_limit(user: User) -> None:
    """Raise 402 when the signed-in user has burned through the trial."""
    if user.scans_used >= FREE_SCAN_LIMIT:
        raise HTTPException(
            status_code=402,
            detail=_PAYWALL_DETAIL.format(limit=FREE_SCAN_LIMIT),
        )


def _bump_scan_count(db: Session, user: User) -> None:
    user.scans_used = (user.scans_used or 0) + 1
    db.commit()


def _gemini_error_to_http(e: genai_errors.APIError, route: str) -> HTTPException:
    """Convert a Gemini upstream error into a FastAPI HTTPException, AND log
    the full upstream message+code so we can actually diagnose 4xx/5xx in
    Render's logs (instead of staring at opaque 'AI service error (400)')."""
    upstream = getattr(e, "code", 500)
    upstream_message = getattr(e, "message", None) or str(e)
    logger.error(
        "Gemini upstream error on %s — code=%s message=%s",
        route,
        upstream,
        upstream_message,
        exc_info=True,
    )
    if upstream in (429, 503):
        return HTTPException(
            status_code=503,
            detail="The AI service is briefly busy. Please try again in a few seconds.",
        )
    if upstream == 400:
        # Almost always the image itself — too small, corrupt, blocked by
        # safety filter, or wrong format. Give a hint the user can act on.
        return HTTPException(
            status_code=400,
            detail=(
                "Couldn't read this image. Try a clearer, larger photo of "
                "a single dish — straight-on, well-lit."
            ),
        )
    return HTTPException(status_code=502, detail=f"AI service error ({upstream}).")


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
    user = optional_current_user(authorization=authorization, db=db)
    if user is not None:
        _enforce_scan_limit(user)
    try:
        core = await analyze_with_vision(data, mime_type=mime)
    except RuntimeError as e:
        # Configuration / parse failures (missing API key, malformed model output).
        raise HTTPException(status_code=500, detail=str(e)) from e
    except genai_errors.APIError as e:
        raise _gemini_error_to_http(e, route="/analyze-vision") from e
    if user is not None:
        _bump_scan_count(db, user)
    summary = _today_summary(db, user) if user else None
    return AnalyzeResponse(**core.model_dump(), daily_summary=summary)


@app.post("/analyze-plate", response_model=PlateAnalyzeResponse)
async def analyze_plate(
    image: UploadFile = File(...),
    user: User = Depends(current_user),
    db: Session = Depends(get_db),
) -> PlateAnalyzeResponse:
    """Single-plate photo analyzer for the Plate tab.

    Always authenticated — the verdict is personalised against the user's
    daily target and what they've already logged today. Use /analyze-vision
    instead for unauthenticated cart screenshots.
    """
    data = await image.read()
    if not data:
        raise HTTPException(status_code=400, detail="Empty image upload.")
    if len(data) > _MAX_IMAGE_BYTES:
        raise HTTPException(
            status_code=413,
            detail=f"Image too large ({len(data)} bytes). Max {_MAX_IMAGE_BYTES}.",
        )
    _enforce_scan_limit(user)
    summary = _today_summary(db, user)
    mime = image.content_type or "image/jpeg"
    logger.info(
        "/analyze-plate user_id=%s image_bytes=%d mime=%s",
        user.id, len(data), mime,
    )
    try:
        core = await analyze_plate_with_vision(data, daily_summary=summary, mime_type=mime)
    except RuntimeError as e:
        raise HTTPException(status_code=500, detail=str(e)) from e
    except genai_errors.APIError as e:
        raise _gemini_error_to_http(e, route="/analyze-plate") from e

    _bump_scan_count(db, user)
    return PlateAnalyzeResponse(**core.model_dump(), daily_summary=summary)


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
async def update_me(
    req: ProfileUpdate,
    user: User = Depends(current_user),
    db: Session = Depends(get_db),
) -> UserPublic:
    changed: set[str] = set()
    if req.daily_kcal_target is not None and req.daily_kcal_target != user.daily_kcal_target:
        user.daily_kcal_target = req.daily_kcal_target
        changed.add("daily_kcal_target")
    if req.birth_year is not None and req.birth_year != user.birth_year:
        user.birth_year = req.birth_year
        changed.add("birth_year")
    if req.sex is not None:
        sex = req.sex.strip().lower()
        if sex not in VALID_SEXES:
            raise HTTPException(
                status_code=422,
                detail=f"sex must be one of {sorted(VALID_SEXES)}",
            )
        if sex != user.sex:
            user.sex = sex
            changed.add("sex")
    if req.weight_kg is not None and req.weight_kg != user.weight_kg:
        user.weight_kg = req.weight_kg
        changed.add("weight_kg")
    if req.height_cm is not None and req.height_cm != user.height_cm:
        user.height_cm = req.height_cm
        changed.add("height_cm")
    if req.activity_level is not None:
        level = req.activity_level.strip().lower()
        if level not in VALID_ACTIVITY_LEVELS:
            raise HTTPException(
                status_code=422,
                detail=f"activity_level must be one of {sorted(VALID_ACTIVITY_LEVELS)}",
            )
        if level != user.activity_level:
            user.activity_level = level
            changed.add("activity_level")
    if req.goal is not None:
        goal = req.goal.strip().lower()
        if goal not in VALID_GOALS:
            raise HTTPException(
                status_code=422,
                detail=f"goal must be one of {sorted(VALID_GOALS)}",
            )
        if goal != user.goal:
            user.goal = goal
            changed.add("goal")
    db.commit()
    db.refresh(user)
    await _maybe_refresh_insight(user, db, changed)
    return _to_user_public(user)


# --- Avatar -----------------------------------------------------------------

_MAX_AVATAR_BYTES = 2 * 1024 * 1024  # 2 MB — plenty for a square JPEG.


@app.post("/me/avatar", response_model=UserPublic)
async def upload_avatar(
    image: UploadFile = File(...),
    user: User = Depends(current_user),
    db: Session = Depends(get_db),
) -> UserPublic:
    """Upload a profile photo. Bytes are stored on the user row and served
    publicly from GET /avatars/{user_id}."""
    data = await image.read()
    if not data:
        raise HTTPException(status_code=400, detail="Empty image upload.")
    if len(data) > _MAX_AVATAR_BYTES:
        raise HTTPException(
            status_code=413,
            detail=f"Image too large ({len(data)} bytes). Max {_MAX_AVATAR_BYTES}.",
        )
    user.avatar_bytes = data
    user.avatar_content_type = image.content_type or "image/jpeg"
    user.avatar_updated_at = datetime.utcnow()
    db.commit()
    db.refresh(user)
    return _to_user_public(user)


@app.delete("/me/avatar", response_model=UserPublic)
def delete_avatar(
    user: User = Depends(current_user),
    db: Session = Depends(get_db),
) -> UserPublic:
    user.avatar_bytes = None
    user.avatar_content_type = None
    user.avatar_updated_at = None
    db.commit()
    db.refresh(user)
    return _to_user_public(user)


@app.get("/avatars/{user_id}")
def get_avatar(
    user_id: int,
    db: Session = Depends(get_db),
) -> Response:
    """Public — returns the user's uploaded avatar bytes. Avatars are
    expected to be cacheable; clients add a `?v=updated_at` query param
    to bust the cache when the user changes their photo."""
    user = db.query(User).filter_by(id=user_id).first()
    if user is None or user.avatar_bytes is None:
        raise HTTPException(status_code=404, detail="No avatar.")
    return Response(
        content=user.avatar_bytes,
        media_type=user.avatar_content_type or "image/jpeg",
        headers={"Cache-Control": "public, max-age=300"},
    )


# --- Meal logging -----------------------------------------------------------


@app.get("/me/today", response_model=TodaySummary)
def me_today(
    user: User = Depends(current_user),
    db: Session = Depends(get_db),
) -> TodaySummary:
    return _today_summary(db, user)


def _meal_log_to_public(log: MealLog) -> MealLogPublic:
    raw_items = json_lib.loads(log.items_json) if log.items_json else []
    items = [MatchedItem.model_validate(item) for item in raw_items]
    return MealLogPublic(
        id=log.id,
        logged_at=log.logged_at.isoformat(),
        kcal=log.kcal,
        protein_g=log.protein_g,
        fat_g=log.fat_g,
        carbs_g=log.carbs_g,
        health_score=log.health_score,
        items=items,
    )


@app.get("/me/meal-logs/today", response_model=list[MealLogPublic])
def me_meal_logs_today(
    user: User = Depends(current_user),
    db: Session = Depends(get_db),
) -> list[MealLogPublic]:
    """Today's logged meals for the current user, most recent first."""
    logs = (
        db.query(MealLog)
        .filter(MealLog.user_id == user.id, MealLog.log_date == date.today())
        .order_by(MealLog.logged_at.desc())
        .all()
    )
    return [_meal_log_to_public(log) for log in logs]


@app.get("/me/daily-summaries", response_model=HistoryResponse)
def me_daily_summaries(
    days: int = 7,
    user: User = Depends(current_user),
    db: Session = Depends(get_db),
) -> HistoryResponse:
    """Per-day kcal + macro totals for the last `days` days (default 7).

    Returns one entry per day, oldest first, including days with zero
    logs (so the chart has a continuous x-axis). Plus a streak count and
    "days the user hit their goal" count for the streak card."""
    days = max(1, min(days, 90))
    today = date.today()
    earliest = today - timedelta(days=days - 1)

    rows = (
        db.query(MealLog)
        .filter(
            MealLog.user_id == user.id,
            MealLog.log_date >= earliest,
            MealLog.log_date <= today,
        )
        .all()
    )

    aggregated: dict[date, DailySummaryPoint] = {}
    for row in rows:
        bucket = aggregated.setdefault(
            row.log_date,
            DailySummaryPoint(date=row.log_date.isoformat()),
        )
        bucket.kcal      += row.kcal
        bucket.protein_g += row.protein_g
        bucket.fat_g     += row.fat_g
        bucket.carbs_g   += row.carbs_g
        bucket.log_count += 1

    series: list[DailySummaryPoint] = []
    for offset in range(days):
        d = earliest + timedelta(days=offset)
        series.append(aggregated.get(d, DailySummaryPoint(date=d.isoformat())))

    target = user.daily_kcal_target or 2000
    goal_hits = sum(1 for p in series if p.log_count > 0 and p.kcal <= target)

    streak = 0
    for point in reversed(series):
        if point.log_count > 0:
            streak += 1
        else:
            break

    return HistoryResponse(
        daily_kcal_target=target,
        days=series,
        streak_days=streak,
        goal_hits=goal_hits,
    )


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
