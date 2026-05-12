from fastapi.testclient import TestClient

from foodlens_backend.analyze import analyze_items
from foodlens_backend.main import app
from foodlens_backend.schemas import AnalyzeRequest

client = TestClient(app)


def test_healthz() -> None:
    response = client.get("/healthz")
    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_analyze_pitch_cart() -> None:
    """The exact cart from the pitch deck (slide 4): Veg Biryani, Paneer Butter
    Masala, Garlic Naan. Should produce a recognizable, scored response."""
    response = client.post(
        "/analyze",
        json={
            "items": ["Veg Biryani", "Paneer Butter Masala", "Garlic Naan"],
            "region_hint": "IN",
        },
    )
    assert response.status_code == 200
    body = response.json()
    assert body["macros"]["kcal"] > 0
    assert 0 < body["health_score"] <= 95
    assert body["health_label"]
    assert len(body["suggestions"]) >= 1
    assert len(body["items"]) == 3


def test_quantity_parsing() -> None:
    """Butter Naan x 2 should count twice."""
    one = analyze_items(AnalyzeRequest(items=["Butter Naan"]))
    two = analyze_items(AnalyzeRequest(items=["Butter Naan x 2"]))
    assert two.macros.kcal == 2 * one.macros.kcal


def test_unknown_items_are_dropped_but_returned_as_unmatched() -> None:
    result = analyze_items(AnalyzeRequest(items=["asdfgh", "Roti", "Andhra Mutton Pepper Fry"]))
    # Only roti counts toward nutrition.
    assert result.macros.kcal == 104
    assert len(result.items) == 1
    assert result.items[0].name == "Roti"
    # The two unmatchable strings come back so the bubble can show
    # "AI couldn't identify: …" — preserves user trust when totals look low.
    assert "asdfgh" in result.unmatched
    assert "Andhra Mutton Pepper Fry" in result.unmatched
    assert "Roti" not in result.unmatched


def test_naan_triggers_swap_suggestion() -> None:
    result = analyze_items(AnalyzeRequest(items=["Butter Naan", "Dal Tadka"]))
    assert any("naan" in s.text.lower() for s in result.suggestions)
    assert result.suggestions[0].kcal_delta < 0


def test_empty_cart() -> None:
    result = analyze_items(AnalyzeRequest(items=[]))
    assert result.macros.kcal == 0
    assert result.percent_daily_kcal == 0
    assert result.health_label == "No food detected"
    assert result.items == []


def test_percent_daily_kcal_is_reasonable() -> None:
    result = analyze_items(AnalyzeRequest(items=["Mutton Biryani"]))
    # ~610 kcal / 2000 = 30%
    assert 25 <= result.percent_daily_kcal <= 35


def test_high_protein_low_fat_scores_higher_than_heavy_meal() -> None:
    healthy = analyze_items(AnalyzeRequest(items=["Dal Tadka", "Roti", "Buttermilk"]))
    heavy = analyze_items(AnalyzeRequest(items=["Butter Chicken", "Butter Naan", "Coke"]))
    assert healthy.health_score > heavy.health_score


def test_items_breakdown_per_match() -> None:
    """Per-item rows must reflect the canonical name, the parsed qty, and the
    multiplied kcal so the bubble can render a line per item."""
    result = analyze_items(AnalyzeRequest(items=["Veg Biryani", "Garlic Naan x 2"]))
    names = {it.name: it for it in result.items}
    assert "Veg Biryani" in names
    assert "Garlic Naan" in names
    assert names["Garlic Naan"].qty == 2
    assert names["Garlic Naan"].kcal == 2 * 290


def test_duplicate_items_merge_quantities() -> None:
    """If OCR returns the same item twice (common when cart layout has the
    name on two lines), the analyzer should merge them rather than count
    them as two unrelated rows."""
    result = analyze_items(AnalyzeRequest(items=["Roti", "Roti"]))
    assert len(result.items) == 1
    assert result.items[0].qty == 2
    assert result.macros.kcal == 2 * 104


def test_alias_resolves() -> None:
    result = analyze_items(AnalyzeRequest(items=["Coca-Cola", "Chai"]))
    names = [it.name for it in result.items]
    assert "Coke" in names
    assert "Masala Chai" in names


def test_classic_does_not_match_lassi() -> None:
    """Real bug from a Domino's screenshot: 'Classic Hand Tossed' was
    matching 'lassi' because the substring matcher saw 'lassi' inside
    'classic'. Word-boundary matching must prevent this."""
    result = analyze_items(AnalyzeRequest(items=["Classic Hand Tossed"]))
    names = [it.name for it in result.items]
    assert "Lassi" not in names
    # Either nothing matched, or only a real pizza-keyword match if we add one.


def test_naan_does_not_match_inside_banana() -> None:
    result = analyze_items(AnalyzeRequest(items=["Banana smoothie"]))
    names = [it.name for it in result.items]
    assert "Naan" not in names


def test_radio_button_prefix_is_stripped() -> None:
    """Domino's radio buttons OCR as 'O ' before the item name."""
    bare = analyze_items(AnalyzeRequest(items=["Burger Pizza - Classic"]))
    prefixed = analyze_items(AnalyzeRequest(items=["O Burger Pizza - Classic"]))
    assert bare.macros.kcal == prefixed.macros.kcal
    assert bare.macros.kcal > 0
    names = [it.name for it in prefixed.items]
    assert any("Burger Pizza" in n for n in names)


def test_dominos_cart_resolves_pizza_items() -> None:
    """The actual Domino's cart from field testing should now resolve."""
    result = analyze_items(
        AnalyzeRequest(
            items=[
                "O Burger Pizza - Classic",
                "O Paneer & Capsicum, Pizza Mania, Classic Hand Tossed, Reg... v",
            ],
        ),
    )
    assert result.macros.kcal > 1000  # two pizzas
    names = [it.name for it in result.items]
    assert any("Burger Pizza" in n for n in names)
    assert any("Paneer" in n or "Pizza Mania" in n for n in names)
