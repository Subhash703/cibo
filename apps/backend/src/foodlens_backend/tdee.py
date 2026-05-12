"""Personalised daily kcal target via the Mifflin-St Jeor equation.

This is the standard formula used by clinical dietitians and major fitness
apps. It produces a *suggested* TDEE (Total Daily Energy Expenditure) — a
non-judgemental neutral estimate based on the user's body stats and activity
level. The user retains full control of their actual goal slider.

Reference: Mifflin et al. (1990), American Journal of Clinical Nutrition.
"""

from __future__ import annotations

from datetime import date

from .models import User

_ACTIVITY_FACTORS: dict[str, float] = {
    "sedentary": 1.2,    # mostly desk work, little/no exercise
    "light": 1.375,      # light exercise 1-3 days/week
    "moderate": 1.55,    # moderate exercise 3-5 days/week
    "active": 1.725,     # heavy exercise 6-7 days/week
    "very_active": 1.9,  # very heavy / athletic training
}

# Sex offset in BMR formula. "other" / unspecified uses the midpoint of
# male (+5) and female (-161) — purely a non-judgemental fallback for
# users who don't want to specify, NOT a clinical recommendation.
_SEX_OFFSETS: dict[str, float] = {
    "male": 5.0,
    "female": -161.0,
    "other": -78.0,
}

VALID_ACTIVITY_LEVELS: frozenset[str] = frozenset(_ACTIVITY_FACTORS.keys())
VALID_SEXES: frozenset[str] = frozenset(_SEX_OFFSETS.keys())


def compute_tdee(user: User) -> int | None:
    """Return suggested daily kcal target, or None if any input is missing
    or out of plausible range. The bubble UI only shows the suggestion when
    this returns a non-None value."""
    if (
        user.weight_kg is None
        or user.height_cm is None
        or user.birth_year is None
        or user.activity_level is None
    ):
        return None
    age = date.today().year - user.birth_year
    if age < 10 or age > 120:
        return None
    if not (20 <= user.weight_kg <= 300):
        return None
    if not (80 <= user.height_cm <= 250):
        return None
    factor = _ACTIVITY_FACTORS.get(user.activity_level)
    if factor is None:
        return None
    sex_offset = _SEX_OFFSETS.get(user.sex or "other", _SEX_OFFSETS["other"])

    bmr = 10.0 * user.weight_kg + 6.25 * user.height_cm - 5.0 * age + sex_offset
    tdee = bmr * factor
    return max(1000, min(5000, int(round(tdee))))
