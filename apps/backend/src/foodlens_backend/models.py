"""SQLAlchemy ORM models for Cibo's user-side state."""

from __future__ import annotations

from datetime import date, datetime

from sqlalchemy import ForeignKey, LargeBinary, String, Text
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column


class Base(DeclarativeBase):
    pass


class User(Base):
    """Cibo user account.

    Auth: email + password (bcrypt). [google_sub] reserved for future OAuth.

    Body stats (all optional) feed the Mifflin-St Jeor TDEE estimator that
    suggests a personalised daily kcal target. The user can ignore them and
    keep editing the slider freely — these fields never override the
    user-chosen [daily_kcal_target].
    """

    __tablename__ = "users"

    id: Mapped[int] = mapped_column(primary_key=True)
    email: Mapped[str] = mapped_column(String(255), unique=True, index=True)
    password_hash: Mapped[str | None] = mapped_column(String(255), nullable=True, default=None)
    name: Mapped[str | None] = mapped_column(String(255), nullable=True, default=None)
    picture: Mapped[str | None] = mapped_column(String(512), nullable=True, default=None)
    daily_kcal_target: Mapped[int] = mapped_column(default=2000)
    google_sub: Mapped[str | None] = mapped_column(
        String(255), unique=True, nullable=True, default=None, index=True,
    )
    created_at: Mapped[datetime] = mapped_column(default=datetime.utcnow)

    # Optional body stats — used only to compute a suggested kcal target.
    birth_year: Mapped[int | None] = mapped_column(nullable=True, default=None)
    sex: Mapped[str | None] = mapped_column(String(16), nullable=True, default=None)
    weight_kg: Mapped[float | None] = mapped_column(nullable=True, default=None)
    height_cm: Mapped[float | None] = mapped_column(nullable=True, default=None)
    activity_level: Mapped[str | None] = mapped_column(String(16), nullable=True, default=None)

    # User-stated goal (lose_weight, build_muscle, …). Drives the
    # personalised AI insight shown on the Goal card.
    goal: Mapped[str | None] = mapped_column(String(32), nullable=True, default=None)
    goal_insight: Mapped[str | None] = mapped_column(Text, nullable=True, default=None)
    goal_insight_at: Mapped[datetime | None] = mapped_column(nullable=True, default=None)

    # Avatar — stored in DB so it survives Render's ephemeral disk.
    # Served from GET /avatars/{user_id} (public, no auth).
    avatar_bytes: Mapped[bytes | None] = mapped_column(LargeBinary, nullable=True, default=None)
    avatar_content_type: Mapped[str | None] = mapped_column(String(32), nullable=True, default=None)
    avatar_updated_at: Mapped[datetime | None] = mapped_column(nullable=True, default=None)

    # Free-tier scan counter. Incremented on every authenticated
    # /analyze-vision and /analyze-plate. Once it hits FREE_SCAN_LIMIT,
    # those routes return 402 Payment Required until premium ships.
    scans_used: Mapped[int] = mapped_column(default=0)


class MealLog(Base):
    """A single confirmed order — one row per 'Yes, I'm ordering' tap."""

    __tablename__ = "meal_logs"

    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    log_date: Mapped[date] = mapped_column(index=True)
    logged_at: Mapped[datetime] = mapped_column(default=datetime.utcnow)
    kcal: Mapped[int]
    protein_g: Mapped[int]
    fat_g: Mapped[int]
    carbs_g: Mapped[int]
    health_score: Mapped[int]
    items_json: Mapped[str] = mapped_column(Text, default="[]")
