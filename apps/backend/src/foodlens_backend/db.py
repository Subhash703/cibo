"""Database engine + session for Cibo.

Postgres-only. No SQLite fallback — the production target is Postgres
(Render / Fly / Neon / Supabase) and we want dev-prod parity, so even
local development runs against a real Postgres instance.

Set `DATABASE_URL` in `apps/backend/.env`. See `.env.example` for the
exact format. The driver is psycopg 3 (`postgresql+psycopg://...`).
"""

from __future__ import annotations

import os
from collections.abc import Generator

from sqlalchemy import create_engine, text
from sqlalchemy.orm import Session, sessionmaker

from .models import Base

_RAW_DATABASE_URL = os.environ.get("DATABASE_URL")
if not _RAW_DATABASE_URL:
    raise RuntimeError(
        "DATABASE_URL env var is required. Set it in apps/backend/.env "
        "(see .env.example). Postgres-only — local dev expects "
        "`postgresql+psycopg://<user>@localhost:5432/cibo`."
    )


def _normalize_database_url(url: str) -> str:
    """Force the SQLAlchemy URL to use the psycopg 3 driver.

    Managed providers (Render, Heroku-style URLs, Supabase direct strings)
    hand us `postgres://` or `postgresql://`, both of which make
    SQLAlchemy pick the default sync driver (psycopg2) which we don't
    ship. Normalising to `postgresql+psycopg://` keeps the same code
    working across local dev (we set it explicitly) and prod.
    """
    if url.startswith("postgres://"):
        return "postgresql+psycopg://" + url.removeprefix("postgres://")
    if url.startswith("postgresql://") and "+psycopg" not in url.split("://", 1)[0]:
        return "postgresql+psycopg://" + url.removeprefix("postgresql://")
    return url


DATABASE_URL = _normalize_database_url(_RAW_DATABASE_URL)

engine = create_engine(DATABASE_URL)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)


def init_db() -> None:
    Base.metadata.create_all(bind=engine)
    _apply_lightweight_migrations()


# Idempotent ALTER TABLE statements for columns added after the first
# release. SQLAlchemy's create_all only adds *missing tables*, never
# missing columns. Postgres' ADD COLUMN IF NOT EXISTS makes this safe to
# run on every boot. Drop these lines once we adopt Alembic.
_LIGHTWEIGHT_MIGRATIONS = (
    "ALTER TABLE users ADD COLUMN IF NOT EXISTS goal VARCHAR(32)",
    "ALTER TABLE users ADD COLUMN IF NOT EXISTS goal_insight TEXT",
    "ALTER TABLE users ADD COLUMN IF NOT EXISTS goal_insight_at TIMESTAMP",
    "ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_bytes BYTEA",
    "ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_content_type VARCHAR(32)",
    "ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_updated_at TIMESTAMP",
)


def _apply_lightweight_migrations() -> None:
    with engine.begin() as conn:
        for stmt in _LIGHTWEIGHT_MIGRATIONS:
            conn.execute(text(stmt))


def get_db() -> Generator[Session, None, None]:
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
