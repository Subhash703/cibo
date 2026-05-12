"""Tests for the email + password auth endpoints."""

from __future__ import annotations

from collections.abc import Iterator

import pytest
from fastapi.testclient import TestClient

# Env (DATABASE_URL, JWT_SECRET) is set up by conftest.py before this import.
from foodlens_backend.db import Base, engine
from foodlens_backend.main import app

client = TestClient(app)


@pytest.fixture(autouse=True)
def _reset_db() -> Iterator[None]:
    Base.metadata.drop_all(bind=engine)
    Base.metadata.create_all(bind=engine)
    yield
    Base.metadata.drop_all(bind=engine)


def _register(email: str = "alice@example.com", password: str = "supersecret123") -> str:
    response = client.post(
        "/auth/register",
        json={"email": email, "password": password, "name": "Alice"},
    )
    assert response.status_code == 201, response.text
    return response.json()["token"]


def test_register_creates_user_and_returns_token() -> None:
    response = client.post(
        "/auth/register",
        json={"email": "user@example.com", "password": "supersecret123", "name": "User"},
    )
    assert response.status_code == 201
    body = response.json()
    assert "token" in body
    assert body["user"]["email"] == "user@example.com"
    assert body["user"]["name"] == "User"
    assert body["user"]["daily_kcal_target"] == 2000


def test_register_normalises_email_to_lowercase() -> None:
    response = client.post(
        "/auth/register",
        json={"email": "MixedCase@Example.COM", "password": "supersecret123"},
    )
    assert response.status_code == 201
    assert response.json()["user"]["email"] == "mixedcase@example.com"


def test_register_rejects_duplicate_email() -> None:
    _register()
    response = client.post(
        "/auth/register",
        json={"email": "alice@example.com", "password": "anothersecret456"},
    )
    assert response.status_code == 409


def test_register_rejects_short_password() -> None:
    response = client.post(
        "/auth/register",
        json={"email": "u@example.com", "password": "short"},
    )
    assert response.status_code == 422


def test_register_rejects_invalid_email() -> None:
    response = client.post(
        "/auth/register",
        json={"email": "notanemail", "password": "supersecret123"},
    )
    assert response.status_code == 400


def test_login_returns_token_with_correct_password() -> None:
    _register("bob@example.com", "correct-horse-battery-staple")
    response = client.post(
        "/auth/login",
        json={"email": "bob@example.com", "password": "correct-horse-battery-staple"},
    )
    assert response.status_code == 200
    body = response.json()
    assert "token" in body
    assert body["user"]["email"] == "bob@example.com"


def test_login_rejects_wrong_password() -> None:
    _register("eve@example.com", "right-password-99")
    response = client.post(
        "/auth/login",
        json={"email": "eve@example.com", "password": "wrong-password"},
    )
    assert response.status_code == 401


def test_login_rejects_unknown_email() -> None:
    response = client.post(
        "/auth/login",
        json={"email": "nobody@example.com", "password": "anything12345"},
    )
    assert response.status_code == 401


def test_get_me_requires_auth_header() -> None:
    response = client.get("/me")
    assert response.status_code == 401


def test_get_me_with_invalid_token_returns_401() -> None:
    response = client.get("/me", headers={"Authorization": "Bearer not-a-real-token"})
    assert response.status_code == 401


def test_get_me_after_register_returns_profile() -> None:
    token = _register("charlie@example.com")
    response = client.get("/me", headers={"Authorization": f"Bearer {token}"})
    assert response.status_code == 200
    assert response.json()["email"] == "charlie@example.com"


def test_patch_me_updates_daily_kcal_target() -> None:
    token = _register()
    response = client.patch(
        "/me",
        headers={"Authorization": f"Bearer {token}"},
        json={"daily_kcal_target": 1800},
    )
    assert response.status_code == 200
    assert response.json()["daily_kcal_target"] == 1800


def test_patch_me_validates_target_range() -> None:
    token = _register()
    response = client.patch(
        "/me",
        headers={"Authorization": f"Bearer {token}"},
        json={"daily_kcal_target": 100},  # below ge=1000
    )
    assert response.status_code == 422
