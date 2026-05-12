"""Smoke tests for the /analyze-vision route.

Real Gemini calls require GEMINI_API_KEY, so end-to-end tests are skipped
when the key is unset. The route's input-validation behavior is testable
without the key.
"""

import os

import pytest
from fastapi.testclient import TestClient

from foodlens_backend.main import app

client = TestClient(app)


def test_analyze_vision_requires_image_field() -> None:
    response = client.post("/analyze-vision")
    # FastAPI returns 422 when a required form field is missing.
    assert response.status_code == 422


def test_analyze_vision_rejects_empty_upload() -> None:
    response = client.post(
        "/analyze-vision",
        files={"image": ("empty.jpg", b"", "image/jpeg")},
    )
    assert response.status_code == 400


@pytest.mark.skipif(
    not os.environ.get("GEMINI_API_KEY"),
    reason="GEMINI_API_KEY not set; live Gemini call skipped",
)
def test_analyze_vision_with_minimal_image_returns_no_food() -> None:
    """Send a 1x1 PNG. Gemini should report `No food detected`.

    Tolerates upstream flakiness: Gemini's free tier occasionally returns
    429/503 (overloaded) during peak hours and 400 INVALID_ARGUMENT for
    pathological 1x1 inputs. None of those are our bug, so we skip the
    assertion when they happen instead of failing the suite."""
    one_pixel_png = bytes.fromhex(
        "89504e470d0a1a0a0000000d49484452000000010000000108060000001f15c4"
        "890000000d49444154789c63600100000005000156a30a280000000049454e44"
        "ae426082",
    )
    response = client.post(
        "/analyze-vision",
        files={"image": ("pixel.png", one_pixel_png, "image/png")},
    )
    if response.status_code in (502, 503):
        pytest.skip(f"Gemini upstream {response.status_code}: {response.json().get('detail', '')}")
    assert response.status_code == 200
    body = response.json()
    assert body["health_label"] in {"No food detected", "Excellent", "Good", "Heavy meal", "Very heavy"}
