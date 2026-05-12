"""Tests for /analyze-plate input validation + auth.

Live Gemini calls are skipped without GEMINI_API_KEY. The contract tests
(missing image, missing auth, empty upload) all run unconditionally and
don't hit Gemini.
"""

from __future__ import annotations

import os
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


def _register_and_get_token() -> str:
    response = client.post(
        "/auth/register",
        json={"email": "plate-test@example.com", "password": "platetestpass1"},
    )
    assert response.status_code == 201, response.text
    return response.json()["token"]


def test_analyze_plate_requires_auth() -> None:
    response = client.post(
        "/analyze-plate",
        files={"image": ("plate.jpg", b"fake-jpeg-bytes", "image/jpeg")},
    )
    assert response.status_code == 401


def test_analyze_plate_requires_image_field() -> None:
    token = _register_and_get_token()
    response = client.post(
        "/analyze-plate",
        headers={"Authorization": f"Bearer {token}"},
    )
    assert response.status_code == 422


def test_analyze_plate_rejects_empty_upload() -> None:
    token = _register_and_get_token()
    response = client.post(
        "/analyze-plate",
        headers={"Authorization": f"Bearer {token}"},
        files={"image": ("empty.jpg", b"", "image/jpeg")},
    )
    assert response.status_code == 400


@pytest.mark.skipif(
    not os.environ.get("GEMINI_API_KEY"),
    reason="GEMINI_API_KEY not set; live Gemini call skipped",
)
def test_analyze_plate_with_synthetic_image_returns_verdict() -> None:
    """Send a real food photo if you've generated /tmp/foodlens_cart.jpg from
    earlier tests. Tolerates upstream 429/503 like the cart test does."""
    image_path = "/tmp/foodlens_cart.jpg"
    if not os.path.exists(image_path):
        pytest.skip(f"{image_path} not present; generate with PIL first")
    token = _register_and_get_token()
    with open(image_path, "rb") as f:
        response = client.post(
            "/analyze-plate",
            headers={"Authorization": f"Bearer {token}"},
            files={"image": ("plate.jpg", f.read(), "image/jpeg")},
        )
    if response.status_code in (502, 503):
        pytest.skip(f"Gemini upstream {response.status_code}")
    assert response.status_code == 200
    body = response.json()
    assert body["verdict"]["signal"] in {"green", "yellow", "red"}
    assert body["verdict"]["one_liner"]
    assert body["daily_summary"] is not None
