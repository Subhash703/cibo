"""Tests for TDEE computation + body-stats profile updates."""

from __future__ import annotations

from collections.abc import Iterator
from datetime import date

import pytest
from fastapi.testclient import TestClient

from foodlens_backend.db import Base, engine
from foodlens_backend.main import app
from foodlens_backend.models import User
from foodlens_backend.tdee import compute_tdee

client = TestClient(app)


@pytest.fixture(autouse=True)
def _reset_db() -> Iterator[None]:
    Base.metadata.drop_all(bind=engine)
    Base.metadata.create_all(bind=engine)
    yield
    Base.metadata.drop_all(bind=engine)


def _user(**overrides) -> User:
    defaults = {
        "email": "u@example.com",
        "daily_kcal_target": 2000,
        "birth_year": date.today().year - 30,
        "sex": "male",
        "weight_kg": 75.0,
        "height_cm": 175.0,
        "activity_level": "moderate",
    }
    return User(**(defaults | overrides))


def test_tdee_male_moderate_30() -> None:
    """Mifflin-St Jeor for a 30y/o male, 75kg, 175cm, moderate activity:
    BMR = 10*75 + 6.25*175 - 5*30 + 5 = 750 + 1093.75 - 150 + 5 = 1698.75
    TDEE = 1698.75 * 1.55 = ~2633 kcal."""
    assert compute_tdee(_user()) == 2633


def test_tdee_female_sedentary_50() -> None:
    """50y/o female, 60kg, 162cm, sedentary:
    BMR = 10*60 + 6.25*162 - 5*50 - 161 = 600 + 1012.5 - 250 - 161 = 1201.5
    TDEE = 1201.5 * 1.2 = ~1442 kcal."""
    user = _user(
        sex="female",
        weight_kg=60.0,
        height_cm=162.0,
        birth_year=date.today().year - 50,
        activity_level="sedentary",
    )
    assert compute_tdee(user) == 1442


def test_tdee_other_uses_midpoint() -> None:
    """'other' sex offset is the midpoint of male/female: -78.
    Should land between male and female TDEEs for the same body."""
    male = compute_tdee(_user(sex="male"))
    female = compute_tdee(_user(sex="female"))
    other = compute_tdee(_user(sex="other"))
    assert male is not None and female is not None and other is not None
    assert female < other < male


def test_tdee_returns_none_when_anything_missing() -> None:
    assert compute_tdee(_user(weight_kg=None)) is None
    assert compute_tdee(_user(height_cm=None)) is None
    assert compute_tdee(_user(birth_year=None)) is None
    assert compute_tdee(_user(activity_level=None)) is None


def test_tdee_returns_none_for_implausible_values() -> None:
    assert compute_tdee(_user(weight_kg=10.0)) is None      # too light
    assert compute_tdee(_user(weight_kg=400.0)) is None     # too heavy
    assert compute_tdee(_user(height_cm=50.0)) is None      # too short
    assert compute_tdee(_user(birth_year=date.today().year - 5)) is None   # too young
    assert compute_tdee(_user(birth_year=1800)) is None     # too old


def test_tdee_returns_none_for_unknown_activity_level() -> None:
    assert compute_tdee(_user(activity_level="hyperdrive")) is None


def _register_and_get_token() -> str:
    response = client.post(
        "/auth/register",
        json={"email": "u@example.com", "password": "supersecret123", "name": "U"},
    )
    assert response.status_code == 201, response.text
    return response.json()["token"]


def test_get_me_does_not_include_suggestion_until_stats_filled() -> None:
    token = _register_and_get_token()
    response = client.get("/me", headers={"Authorization": f"Bearer {token}"})
    body = response.json()
    assert body["suggested_kcal_target"] is None
    assert body["birth_year"] is None
    assert body["weight_kg"] is None


def test_patch_me_with_full_body_stats_returns_suggestion() -> None:
    token = _register_and_get_token()
    response = client.patch(
        "/me",
        headers={"Authorization": f"Bearer {token}"},
        json={
            "birth_year": date.today().year - 30,
            "sex": "male",
            "weight_kg": 75.0,
            "height_cm": 175.0,
            "activity_level": "moderate",
        },
    )
    assert response.status_code == 200
    body = response.json()
    assert body["birth_year"] == date.today().year - 30
    assert body["sex"] == "male"
    assert body["weight_kg"] == 75.0
    assert body["activity_level"] == "moderate"
    assert body["suggested_kcal_target"] == 2633
    # User-chosen target is unchanged — physique stats are *suggestions*.
    assert body["daily_kcal_target"] == 2000


def test_patch_me_partial_body_stats_keeps_suggestion_null() -> None:
    """If only some of the four required stats are filled in, no suggestion."""
    token = _register_and_get_token()
    response = client.patch(
        "/me",
        headers={"Authorization": f"Bearer {token}"},
        json={"weight_kg": 70.0, "height_cm": 170.0},  # missing birth_year + activity
    )
    body = response.json()
    assert body["weight_kg"] == 70.0
    assert body["suggested_kcal_target"] is None


def test_patch_me_rejects_invalid_sex() -> None:
    token = _register_and_get_token()
    response = client.patch(
        "/me",
        headers={"Authorization": f"Bearer {token}"},
        json={"sex": "alien"},
    )
    assert response.status_code == 422


def test_patch_me_rejects_invalid_activity_level() -> None:
    token = _register_and_get_token()
    response = client.patch(
        "/me",
        headers={"Authorization": f"Bearer {token}"},
        json={"activity_level": "lightspeed"},
    )
    assert response.status_code == 422


def test_patch_me_rejects_out_of_range_weight() -> None:
    token = _register_and_get_token()
    response = client.patch(
        "/me",
        headers={"Authorization": f"Bearer {token}"},
        json={"weight_kg": 500.0},
    )
    assert response.status_code == 422
