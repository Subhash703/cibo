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

from sqlalchemy import create_engine
from sqlalchemy.orm import Session, sessionmaker

from .models import Base

DATABASE_URL = os.environ.get("DATABASE_URL")
if not DATABASE_URL:
    raise RuntimeError(
        "DATABASE_URL env var is required. Set it in apps/backend/.env "
        "(see .env.example). Postgres-only — local dev expects "
        "`postgresql+psycopg://<user>@localhost:5432/cibo`."
    )

engine = create_engine(DATABASE_URL)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)


def init_db() -> None:
    Base.metadata.create_all(bind=engine)


def get_db() -> Generator[Session, None, None]:
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
