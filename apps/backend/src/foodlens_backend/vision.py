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

from .schemas import AnalysisCore

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
    # Fallback: SDK occasionally returns a dict instead of the parsed model.
    if isinstance(parsed, dict):
        return AnalysisCore.model_validate(parsed)
    raise RuntimeError(f"Unexpected Gemini response type: {type(parsed).__name__}")
