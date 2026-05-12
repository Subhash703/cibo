"""Vision-LLM analyzer.

Sends a captured screenshot directly to Gemini 2.5 Flash and asks it to
return a structured [AnalyzeResponse] in one shot. This replaces the
fragile OCR + word-boundary-DB pipeline for the primary path: the model
sees the visual layout (which lines belong to one item, which are
banners, which are quantities) instead of guessing from disjoint text.
"""

from __future__ import annotations

import os
from functools import lru_cache

from google import genai
from google.genai import types

from .schemas import AnalysisCore, PlateAnalysisCore, TodaySummary

_MODEL = "gemini-2.5-flash"

_SYSTEM_PROMPT = """\
You are a cart-nutrition analyst for Indian food delivery and grocery apps.

Look at this screenshot — it's a user's current cart in Swiggy, Zomato,
Domino's, BigBasket, Blinkit, Dunzo, McDonald's, KFC, etc. Extract every
food item that is ACTUALLY IN THE CART. Ignore: menu suggestions,
"you might also like", banners, promotions, restaurant headers,
ratings, delivery-time strips, address bars, and pure UI chrome.

For every cart item, output:
- name: a clean human-readable name (e.g. "Burger Pizza - Classic",
  not "O Burger Pizza"; combine multi-line item names into one).
- qty: integer quantity in the cart (default 1).
- kcal: estimated calories for the QUANTITY ordered, using realistic
  Indian restaurant portions.

Then compute the cart totals across ALL items:
- macros.kcal = sum of item kcal
- macros.protein_g, macros.fat_g, macros.carbs_g = totals in grams
- percent_daily_kcal = round(kcal / 2000 * 100), assuming 2000 kcal/day.

Score the meal:
- health_score: integer 0-100. 85+ = excellent (low-cal, high-protein,
  balanced). 65-84 = good. 45-64 = heavy meal. <45 = very heavy.
- health_label: one of "Excellent", "Good", "Heavy meal", "Very heavy".

Suggest exactly ONE specific, impactful swap. Format like:
"Swap garlic naan for roti — save ~190 kcal" with kcal_delta as a
NEGATIVE integer (savings). If nothing obvious, suggest a side of
vegetables with kcal_delta=0.

If the screenshot is NOT a food cart (Instagram, banking, home screen,
locked screen): return items=[], macros all zero, health_score=50,
health_label="No food detected", a single suggestion with kcal_delta=0,
and unmatched=[].

Always set `unmatched` to an empty array — you have visual context, so
nothing should be unmatched.

Realistic portion guide for India:
- Domino's medium pizza: 800-1100 kcal whole. Pizza Mania (small): 600-700.
- Biryani: 450-620 kcal per restaurant serving.
- Dal/curry: 180-450 depending on richness (dal tadka light, dal makhani heavy).
- Breads: roti 100, naan 260, butter naan 310, garlic naan 290, paratha 280.
- Burger (non-veg fast-food): 350-450. Veg burger: 280.
- Fries (regular): 320. Coke (regular): 140.
- Idli (per piece): 40. Dosa (masala): 290.
"""


@lru_cache(maxsize=1)
def _client() -> genai.Client:
    api_key = os.environ.get("GEMINI_API_KEY")
    if not api_key:
        raise RuntimeError(
            "GEMINI_API_KEY is not set. Get a key at "
            "https://aistudio.google.com/apikey and export it before "
            "starting the server.",
        )
    return genai.Client(api_key=api_key)


async def analyze_with_vision(image_bytes: bytes, mime_type: str = "image/jpeg") -> AnalysisCore:
    image_part = types.Part.from_bytes(data=image_bytes, mime_type=mime_type)
    response = await _client().aio.models.generate_content(
        model=_MODEL,
        contents=[_SYSTEM_PROMPT, image_part],
        config=types.GenerateContentConfig(
            response_mime_type="application/json",
            response_schema=AnalysisCore,
            temperature=0.1,
            # Disable extended reasoning. The prompt already pins the output
            # structure tightly; thinking adds 5-10s of latency for no gain
            # on this kind of bounded-extraction task.
            thinking_config=types.ThinkingConfig(thinking_budget=0),
        ),
    )
    parsed = response.parsed
    if isinstance(parsed, AnalysisCore):
        return parsed
    if isinstance(parsed, dict):
        return AnalysisCore.model_validate(parsed)
    raise RuntimeError(f"Unexpected Gemini response type: {type(parsed).__name__}")


_PLATE_PROMPT_TEMPLATE = """\
You are a friendly nutrition coach for someone who's tracking their daily
intake. Look at this photo: it shows a SINGLE plate / bowl / cup of food the
user is about to eat (NOT a delivery app screenshot — that's a different flow).

Step 1 — identify the dish:
- `dish_name`: the most recognisable name for what's on the plate, in
  the user's region (assume India unless the food strongly suggests
  otherwise). Combine sub-components into one name when they're served
  together (e.g. "Savoury Pancake with Chutney and Curry", not
  three separate items).
- `dish_description`: one warm, descriptive sentence — flavours, textures,
  cuisine context. Think menu copy, not clinical.

Step 2 — estimate nutrition for ONE realistic serving as shown:
- `macros.kcal`, `protein_g`, `fat_g`, `carbs_g` based on typical Indian
  restaurant / home portions.

Step 3 — score the meal in isolation:
- `health_score` 0-100, `health_label` one of
  "Excellent" | "Good" | "Heavy meal" | "Very heavy"

Step 4 — coach verdict GIVEN the user's day so far:
{daily_context}

Set `verdict.signal` to:
- "green"  if eating this leaves them comfortably under their daily goal
- "yellow" if it takes most of the remaining budget but doesn't exceed it
- "red"    if it would push them over goal, or they're already close to it

Set `verdict.one_liner` to a single sentence in second person, warm but
honest. Examples (DO NOT REUSE VERBATIM):
- green:  "Fits comfortably — you'll still have room for dinner."
- yellow: "Works, but this uses up most of today's budget."
- red:    "This would push you over today's goal — consider a smaller portion."

Set `verdict.reason` to a 1-2 sentence explanation with the actual numbers.

If the image is NOT a single plate of food (delivery app screenshot,
landscape, person, etc.): set `dish_name`="Not food", `dish_description`=
empty, all macros 0, health_score=50, health_label="No food detected",
verdict.signal="yellow", verdict.one_liner="Couldn't see a plate of food in
this image — try again with the meal centred."
"""


def _format_daily_context(summary: TodaySummary | None) -> str:
    if summary is None:
        return (
            "The user is NOT signed in — you don't have their daily goal or what "
            "they've eaten so far. Skip personalisation; for `verdict.signal` "
            "use 'green' unless this single meal is clearly very heavy (>900 "
            "kcal, very fat-heavy). `verdict.one_liner` should describe the "
            "meal itself, not their day."
        )
    return (
        f"- Daily kcal goal: {summary.daily_kcal_target}\n"
        f"- Consumed today: {summary.consumed_kcal} kcal\n"
        f"- Remaining today: {summary.remaining_kcal} kcal\n"
        f"- Today's logged meals: {summary.log_count}"
    )


async def analyze_plate_with_vision(
    image_bytes: bytes,
    daily_summary: TodaySummary | None,
    mime_type: str = "image/jpeg",
) -> PlateAnalysisCore:
    """Single-plate analyzer. Different prompt + schema from /analyze-vision."""
    prompt = _PLATE_PROMPT_TEMPLATE.format(daily_context=_format_daily_context(daily_summary))
    image_part = types.Part.from_bytes(data=image_bytes, mime_type=mime_type)
    response = await _client().aio.models.generate_content(
        model=_MODEL,
        contents=[prompt, image_part],
        config=types.GenerateContentConfig(
            response_mime_type="application/json",
            response_schema=PlateAnalysisCore,
            temperature=0.2,  # slight room for warmer verdict copy
            thinking_config=types.ThinkingConfig(thinking_budget=0),
        ),
    )
    parsed = response.parsed
    if isinstance(parsed, PlateAnalysisCore):
        return parsed
    if isinstance(parsed, dict):
        return PlateAnalysisCore.model_validate(parsed)
    raise RuntimeError(f"Unexpected Gemini response type: {type(parsed).__name__}")
