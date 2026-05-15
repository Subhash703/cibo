from pydantic import BaseModel, ConfigDict, Field


class AnalyzeRequest(BaseModel):
    items: list[str] = Field(..., description="Food item names extracted by on-device OCR.")
    region_hint: str | None = Field(default=None, description="ISO region, e.g. 'IN'.")


class Macro(BaseModel):
    kcal: int
    protein_g: int
    fat_g: int
    carbs_g: int


class Suggestion(BaseModel):
    text: str
    kcal_delta: int


class MatchedItem(BaseModel):
    """A single recognized food item with its computed contribution."""

    name: str = Field(..., description="Canonical display name (title case).")
    qty: int = Field(default=1, ge=1)
    kcal: int = Field(..., ge=0)


class TodaySummary(BaseModel):
    """How much the user has consumed today and how much is left."""

    daily_kcal_target: int
    consumed_kcal: int
    remaining_kcal: int
    consumed_protein_g: int = 0
    consumed_fat_g: int = 0
    consumed_carbs_g: int = 0
    log_count: int = 0


class AnalysisCore(BaseModel):
    """The per-cart analysis that the vision LLM produces. NO user-context
    fields here — Gemini fills only this. Daily-summary is layered on by
    the route after the LLM returns."""

    health_score: int = Field(..., ge=0, le=100)
    health_label: str
    macros: Macro
    percent_daily_kcal: int
    suggestions: list[Suggestion]
    items: list[MatchedItem] = Field(default_factory=list)
    unmatched: list[str] = Field(default_factory=list)


class CoachVerdict(BaseModel):
    """Single-plate verdict for the photo-of-my-meal flow. Only populated by
    /analyze-plate, never by /analyze-vision (which is cart-mode)."""

    signal: str = Field(..., description="green / yellow / red")
    one_liner: str = Field(..., description="Single-sentence verdict shown on the result card.")
    reason: str = Field(default="", description="Optional longer explanation; shown when user expands.")


class PlateAnalysisCore(BaseModel):
    """Single-plate version of AnalysisCore. Different prompt shape:
    we want the dish name and a friendly description (not cart-style line
    items), plus the coach verdict that compares against the user's day."""

    dish_name: str
    dish_description: str
    macros: Macro
    health_score: int = Field(..., ge=0, le=100)
    health_label: str
    verdict: CoachVerdict


class AnalyzeResponse(AnalysisCore):
    """Public response for the cart-flow analyzer.

    Identical to [AnalysisCore] plus an optional [daily_summary] populated
    when the request was authenticated."""

    daily_summary: TodaySummary | None = None


class PlateAnalyzeResponse(PlateAnalysisCore):
    """Public response for the plate-flow analyzer.

    Includes the user's daily summary so the result card can render both
    'this dish' and 'your day' side-by-side."""

    daily_summary: TodaySummary | None = None


# --- Auth + profile ---------------------------------------------------------


class RegisterRequest(BaseModel):
    email: str = Field(..., min_length=3, max_length=255)
    password: str = Field(..., min_length=8, max_length=128)
    name: str | None = Field(default=None, max_length=100)


class LoginRequest(BaseModel):
    email: str = Field(..., min_length=3, max_length=255)
    password: str = Field(..., min_length=1, max_length=128)


class UserPublic(BaseModel):
    """User-facing profile shape returned to the Android client."""

    model_config = ConfigDict(from_attributes=True)

    email: str
    name: str | None = None
    picture: str | None = None
    daily_kcal_target: int = 2000

    # Optional body stats. The client renders an opt-in "Personalise my
    # goal" section — none of these are required for the app to work.
    birth_year: int | None = None
    sex: str | None = None  # "male" / "female" / "other"
    weight_kg: float | None = None
    height_cm: float | None = None
    activity_level: str | None = None  # one of tdee.VALID_ACTIVITY_LEVELS

    # Computed (Mifflin-St Jeor TDEE). Non-None only when all required body
    # stats are filled in. Suggestion only — user controls the actual goal
    # via [daily_kcal_target].
    suggested_kcal_target: int | None = None

    # User-stated goal + AI-generated, personalised insight about it.
    # `goal_insight` is regenerated whenever the user updates metrics or
    # picks a new goal — see main.update_me.
    goal: str | None = None
    goal_insight: str | None = None

    # True when the user has uploaded an avatar. Clients render
    # GET /avatars/{user_id} when this is set; otherwise show initials.
    has_avatar: bool = False

    # Free-tier scan budget. Clients show "X of {scans_limit} free scans
    # used" and react to 402 from the analyze routes when the cap is hit.
    scans_used: int = 0
    scans_limit: int = 16


class AuthResponse(BaseModel):
    """Response to /auth/register and /auth/login.

    The Android client stores [token] and sends it on every authenticated
    request as `Authorization: Bearer <token>`. Tokens expire after 30 days;
    the client gracefully falls back to the sign-in screen on 401."""

    token: str
    user: UserPublic


class ProfileUpdate(BaseModel):
    daily_kcal_target: int | None = Field(default=None, ge=1000, le=5000)
    birth_year: int | None = Field(default=None, ge=1900, le=2030)
    sex: str | None = None
    weight_kg: float | None = Field(default=None, ge=20, le=300)
    height_cm: float | None = Field(default=None, ge=80, le=250)
    activity_level: str | None = None
    goal: str | None = None  # one of insights.VALID_GOALS


class MealLogRequest(BaseModel):
    macros: Macro
    items: list[MatchedItem] = Field(default_factory=list)
    health_score: int = Field(..., ge=0, le=100)


class MealLogPublic(BaseModel):
    """A single logged meal, returned by GET /me/meal-logs/today."""

    id: int
    logged_at: str = Field(..., description="ISO 8601 timestamp in UTC.")
    kcal: int
    protein_g: int
    fat_g: int
    carbs_g: int
    health_score: int
    items: list[MatchedItem] = Field(default_factory=list)


class DailySummaryPoint(BaseModel):
    """One day's totals — used by the History screen to draw trends."""

    date: str = Field(..., description="ISO date YYYY-MM-DD (the user's UTC day).")
    kcal: int = 0
    protein_g: int = 0
    fat_g: int = 0
    carbs_g: int = 0
    log_count: int = 0


class HistoryResponse(BaseModel):
    """Response shape for GET /me/daily-summaries."""

    daily_kcal_target: int
    days: list[DailySummaryPoint]
    streak_days: int = Field(default=0, description="Consecutive days the user logged at least one meal, ending today.")
    goal_hits: int = Field(default=0, description="Days within the window where kcal landed at or below the goal.")
