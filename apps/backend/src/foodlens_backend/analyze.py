import re

from .nutrition_db import ALIASES, DB, SWAPS, Macros
from .schemas import AnalyzeRequest, AnalyzeResponse, Macro, MatchedItem, Suggestion

DAILY_KCAL_BASELINE = 2000

_QUANTITY_PATTERNS = [
    re.compile(r"[×x]\s*(\d+)", re.IGNORECASE),
    re.compile(r"\((\d+)\)"),
    re.compile(r"qty\s*[:=]?\s*(\d+)", re.IGNORECASE),
]

# Domino's-style radio-button graphics OCR as a leading "O " (or "0 ")
# before the item name. Strip it before matching.
_RADIO_PREFIX = re.compile(r"^[o0]\s+", re.IGNORECASE)


def _parse_quantity(text: str) -> tuple[str, int]:
    """Returns (cleaned_text, quantity). Quantity defaults to 1."""
    qty = 1
    cleaned = text
    for pattern in _QUANTITY_PATTERNS:
        match = pattern.search(cleaned)
        if match:
            qty = max(qty, int(match.group(1)))
            cleaned = (cleaned[: match.start()] + cleaned[match.end() :]).strip()
    return cleaned, qty


def _normalize_ocr(text: str) -> str:
    """Strip common OCR artifacts before matching."""
    text = text.strip()
    text = _RADIO_PREFIX.sub("", text)
    return text


def _word_boundary_match(key: str, text: str) -> bool:
    """Return True iff `key` appears as whole word(s) inside `text`.

    Both inputs should already be lowercased. Word boundaries prevent
    false positives like 'lassi' matching inside 'classic' or 'naan'
    matching inside 'banana'.
    """
    pattern = r"\b" + re.escape(key) + r"\b"
    return re.search(pattern, text) is not None


def _find_canonical(text: str) -> str | None:
    """Return the longest DB key whose words appear inside `text`."""
    aliased = ALIASES.get(text)
    if aliased:
        return aliased
    if text in DB:
        return text

    best: str | None = None
    for key in DB:
        if _word_boundary_match(key, text) and (best is None or len(key) > len(best)):
            best = key
    return best


def _match_items(
    raw_items: list[str],
) -> tuple[list[tuple[str, Macros, int]], list[str]]:
    """Resolve raw OCR strings.

    Returns (matched, unmatched). Matched items merge by canonical name
    (duplicate OCR hits sum their qty). Unmatched preserves the original
    raw OCR text so the bubble can show 'AI couldn't identify: …' and the
    user understands why nutrition might look off.
    """
    by_name: dict[str, tuple[Macros, int]] = {}
    unmatched: list[str] = []
    for raw in raw_items:
        text = _normalize_ocr(raw.lower())
        if not text:
            continue
        cleaned, qty = _parse_quantity(text)
        canonical = _find_canonical(cleaned)
        if canonical is None:
            unmatched.append(raw.strip())
            continue
        macros = DB[canonical]
        existing = by_name.get(canonical)
        by_name[canonical] = (macros, (existing[1] if existing else 0) + qty)
    matched = [(name, m, q) for name, (m, q) in by_name.items()]
    return matched, unmatched


def _sum_macros(matched: list[tuple[str, Macros, int]]) -> Macro:
    kcal = sum(m.kcal * q for _, m, q in matched)
    protein = sum(m.protein_g * q for _, m, q in matched)
    fat = sum(m.fat_g * q for _, m, q in matched)
    carbs = sum(m.carbs_g * q for _, m, q in matched)
    return Macro(kcal=kcal, protein_g=protein, fat_g=fat, carbs_g=carbs)


def _score(macros: Macro) -> tuple[int, str]:
    kcal = macros.kcal
    if kcal <= 0:
        return 50, "No food detected"

    fat_ratio = (macros.fat_g * 9) / kcal
    protein_ratio = (macros.protein_g * 4) / kcal

    score = 100.0
    score -= max(0.0, (kcal - 600) / 20.0)
    score -= max(0.0, (fat_ratio - 0.30) * 100.0)
    score += min(15.0, protein_ratio * 50.0)

    clamped = max(10, min(95, int(round(score))))

    if clamped >= 85:
        label = "Excellent"
    elif clamped >= 65:
        label = "Good"
    elif clamped >= 45:
        label = "Heavy meal"
    else:
        label = "Very heavy"
    return clamped, label


def _suggest(matched_canonical: list[str]) -> list[Suggestion]:
    matched_set = set(matched_canonical)
    out: list[Suggestion] = []
    for swap in SWAPS:
        if swap.if_present in matched_set:
            out.append(Suggestion(text=swap.suggestion, kcal_delta=-swap.kcal_saved))
            break
    if not out:
        out.append(Suggestion(text="Add a side of vegetables or salad", kcal_delta=0))
    return out


def _build_items(matched: list[tuple[str, Macros, int]]) -> list[MatchedItem]:
    return [
        MatchedItem(name=name.title(), qty=qty, kcal=macros.kcal * qty)
        for name, macros, qty in matched
    ]


def analyze_items(request: AnalyzeRequest) -> AnalyzeResponse:
    matched, unmatched = _match_items(request.items)
    macros = _sum_macros(matched)
    score, label = _score(macros)
    percent_daily = int(round((macros.kcal / DAILY_KCAL_BASELINE) * 100)) if macros.kcal else 0
    suggestions = _suggest([name for name, _, _ in matched])

    return AnalyzeResponse(
        health_score=score,
        health_label=label,
        macros=macros,
        percent_daily_kcal=percent_daily,
        suggestions=suggestions,
        items=_build_items(matched),
        unmatched=unmatched,
    )
