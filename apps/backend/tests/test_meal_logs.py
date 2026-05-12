"""Tests for meal logging + daily summary."""

from __future__ import annotations

from collections.abc import Iterator

import pytest
from fastapi.testclient import TestClient

from foodlens_backend.db import Base, engine
from foodlens_backend.main import app

client = TestClient(app)


@pytest.fixture(autouse=True)
def _reset_db() -> Iterator[None]:
    Base.metadata.drop_all(bind=engine)
    Base.metadata.create_all(bind=engine)
    yield
    Base.metadata.drop_all(bind=engine)


def _register(email: str = "u@example.com", password: str = "supersecret123") -> str:
    response = client.post(
        "/auth/register",
        json={"email": email, "password": password},
    )
    assert response.status_code == 201, response.text
    return response.json()["token"]


def test_today_starts_at_zero() -> None:
    token = _register()
    response = client.get("/me/today", headers={"Authorization": f"Bearer {token}"})
    assert response.status_code == 200
    body = response.json()
    assert body["consumed_kcal"] == 0
    assert body["remaining_kcal"] == body["daily_kcal_target"]
    assert body["log_count"] == 0


def test_log_meal_updates_summary() -> None:
    token = _register()
    response = client.post(
        "/meal-logs",
        headers={"Authorization": f"Bearer {token}"},
        json={
            "macros": {"kcal": 920, "protein_g": 22, "fat_g": 28, "carbs_g": 110},
            "items": [
                {"name": "Burger Pizza", "qty": 1, "kcal": 880},
                {"name": "Coke", "qty": 1, "kcal": 40},
            ],
            "health_score": 55,
        },
    )
    assert response.status_code == 200
    body = response.json()
    assert body["consumed_kcal"] == 920
    assert body["consumed_protein_g"] == 22
    assert body["log_count"] == 1
    assert body["remaining_kcal"] == body["daily_kcal_target"] - 920


def test_log_meal_accumulates_across_orders() -> None:
    token = _register()
    headers = {"Authorization": f"Bearer {token}"}
    client.post(
        "/meal-logs",
        headers=headers,
        json={
            "macros": {"kcal": 500, "protein_g": 20, "fat_g": 15, "carbs_g": 60},
            "items": [],
            "health_score": 70,
        },
    )
    client.post(
        "/meal-logs",
        headers=headers,
        json={
            "macros": {"kcal": 400, "protein_g": 10, "fat_g": 12, "carbs_g": 55},
            "items": [],
            "health_score": 65,
        },
    )
    body = client.get("/me/today", headers=headers).json()
    assert body["consumed_kcal"] == 900
    assert body["log_count"] == 2


def test_remaining_clamps_at_zero_when_over_target() -> None:
    token = _register()
    response = client.post(
        "/meal-logs",
        headers={"Authorization": f"Bearer {token}"},
        json={
            "macros": {"kcal": 2500, "protein_g": 60, "fat_g": 90, "carbs_g": 280},
            "items": [],
            "health_score": 30,
        },
    )
    body = response.json()
    assert body["consumed_kcal"] == 2500
    assert body["remaining_kcal"] == 0


def test_meal_log_requires_auth() -> None:
    response = client.post(
        "/meal-logs",
        json={
            "macros": {"kcal": 100, "protein_g": 5, "fat_g": 5, "carbs_g": 10},
            "items": [],
            "health_score": 70,
        },
    )
    assert response.status_code == 401


def test_users_log_lists_are_isolated() -> None:
    """Two users' logs must not bleed into each other's daily summaries."""
    token_a = _register("a@example.com")
    token_b = _register("b@example.com")
    client.post(
        "/meal-logs",
        headers={"Authorization": f"Bearer {token_a}"},
        json={
            "macros": {"kcal": 300, "protein_g": 10, "fat_g": 5, "carbs_g": 50},
            "items": [],
            "health_score": 80,
        },
    )
    body_b = client.get(
        "/me/today",
        headers={"Authorization": f"Bearer {token_b}"},
    ).json()
    assert body_b["consumed_kcal"] == 0
