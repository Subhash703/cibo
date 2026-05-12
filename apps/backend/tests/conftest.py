"""Pytest configuration shared across all backend tests.

Sets up the environment BEFORE any test module imports the FastAPI app,
which would otherwise trigger db.py's "DATABASE_URL is required" guard.

Tests run against a dedicated `cibo_test` Postgres database. Create it
once with `createdb cibo_test`. Each test fixture drops + recreates the
schema (see the autouse `_reset_db` fixture in each test file), so order
of execution doesn't matter.

Override the URL in CI / sandboxes via the TEST_DATABASE_URL env var.
"""

from __future__ import annotations

import os


def _default_test_db_url() -> str:
    user = (
        os.environ.get("PGUSER")
        or os.environ.get("USER")
        or "postgres"
    )
    return f"postgresql+psycopg://{user}@localhost:5432/cibo_test"


os.environ.setdefault("JWT_SECRET", "test-secret-key-do-not-use-in-prod")
os.environ.setdefault("DATABASE_URL", os.environ.get("TEST_DATABASE_URL") or _default_test_db_url())
