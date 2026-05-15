"""AI-generated goal insight for the Profile screen.

Triggered when the user picks a goal or updates metrics. The result is
cached on `users.goal_insight` so the Profile screen can render
instantly without hitting Gemini on every load.
"""

from __future__ import annotations

import logging
from datetime import datetime

from google.genai import errors as genai_errors
from google.genai import types

from .models import User
from .tdee import compute_tdee
from .vision import _client

logger = logging.getLogger("foodlens.insights")

_MODEL = "gemini-2.5-flash"

# Public list — referenced by the validator in main.update_me.
VALID_GOALS: dict[str, str] = {
    "lose_weight":      "Lose weight",
    "build_muscle":     "Build muscle",
    "stay_healthy":     "Stay healthy",
    "improve_energy":   "Improve energy",
    "manage_diabetes":  "Manage diabetes",
    "general_wellness": "General wellness",
}

_PROMPT_TEMPLATE = """\
You are Cibo, a warm, witty nutrition coach speaking directly to the user.

Their profile:
- Goal: {goal}
- Daily calorie target: {daily_kcal} kcal
- {body_line}

Write ONE short, encouraging insight (2 sentences max, ~30 words total) that:
- Names the goal naturally and ties it to their daily kcal target.
- Drops one specific, actionable tip that fits their goal.
- Sounds like a friend, never preachy. No emoji. No exclamation marks.
- Avoids medical claims; nothing prescriptive about diabetes/conditions
  even if that's the goal — keep it lifestyle-focused.

Output ONLY the insight text — no preface, no "Here's your insight:".
"""


async def generate_goal_insight(user: User) -> str | None:
    """Generate a 2-sentence personalised insight for the user's chosen goal.

    Returns None on any failure (network, parse, missing key) — the caller
    falls back to the previously cached value or the static copy.
    """
    if not user.goal:
        return None

    goal_label = VALID_GOALS.get(user.goal, user.goal)
    body_line = _summarise_body(user)
    prompt = _PROMPT_TEMPLATE.format(
        goal=goal_label,
        daily_kcal=user.daily_kcal_target,
        body_line=body_line,
    )

    try:
        client = _client()
    except RuntimeError as e:
        logger.warning("Gemini not configured, skipping insight: %s", e)
        return None

    try:
        response = await client.aio.models.generate_content(
            model=_MODEL,
            contents=[prompt],
            config=types.GenerateContentConfig(
                temperature=0.6,
                thinking_config=types.ThinkingConfig(thinking_budget=0),
            ),
        )
        text = (response.text or "").strip().strip('"').strip("'")
        if not text:
            return None
        # Trim to ~240 chars so the InsightCard never overflows on small phones.
        return text if len(text) <= 240 else text[:237].rsplit(" ", 1)[0] + "…"
    except genai_errors.APIError as e:
        logger.warning("Gemini upstream error generating insight: %s", e)
        return None
    except Exception:
        logger.exception("Unexpected failure generating insight")
        return None


def _summarise_body(user: User) -> str:
    parts: list[str] = []
    if user.sex:
        parts.append(user.sex)
    age = _age(user.birth_year)
    if age is not None:
        parts.append(f"{age}y")
    if user.weight_kg:
        parts.append(f"{user.weight_kg:.0f}kg")
    if user.height_cm:
        parts.append(f"{user.height_cm:.0f}cm")
    if user.activity_level:
        parts.append(user.activity_level)

    tdee = compute_tdee(user)
    if tdee:
        parts.append(f"TDEE ~{tdee} kcal")

    return ", ".join(parts) if parts else "no body stats provided"


def _age(birth_year: int | None) -> int | None:
    if not birth_year:
        return None
    return max(0, datetime.utcnow().year - birth_year)
