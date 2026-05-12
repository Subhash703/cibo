"""Hand-curated nutrition table for v0.1.

One typical restaurant serving per row. Numbers are pragmatic averages — they
aren't clinical-grade and shouldn't be presented as medical advice. We replace
this with USDA + IFCT data behind the same interface in a follow-up.
"""

from typing import NamedTuple


class Macros(NamedTuple):
    kcal: int
    protein_g: int
    fat_g: int
    carbs_g: int


# Keys are lowercased canonical names. Aliases map to the same canonical key
# via ALIASES below.
DB: dict[str, Macros] = {
    # Indian breads
    "roti": Macros(104, 3, 2, 18),
    "chapati": Macros(104, 3, 2, 18),
    "tandoori roti": Macros(120, 4, 2, 22),
    "missi roti": Macros(160, 5, 4, 24),
    "naan": Macros(260, 9, 5, 45),
    "butter naan": Macros(310, 8, 12, 45),
    "garlic naan": Macros(290, 8, 10, 44),
    "cheese naan": Macros(390, 14, 18, 42),
    "paratha": Macros(280, 6, 14, 35),
    "aloo paratha": Macros(330, 7, 14, 42),
    "lachha paratha": Macros(310, 7, 16, 36),
    "kulcha": Macros(245, 7, 8, 38),
    # Indian mains — vegetarian
    "paneer butter masala": Macros(420, 18, 28, 22),
    "paneer tikka masala": Macros(380, 22, 24, 20),
    "kadai paneer": Macros(350, 18, 22, 18),
    "shahi paneer": Macros(440, 16, 32, 20),
    "palak paneer": Macros(310, 16, 20, 14),
    "matar paneer": Macros(330, 15, 20, 18),
    "dal makhani": Macros(320, 12, 18, 28),
    "dal tadka": Macros(180, 9, 6, 22),
    "yellow dal": Macros(170, 9, 5, 21),
    "chana masala": Macros(260, 12, 8, 36),
    "chole": Macros(260, 12, 8, 36),
    "rajma": Macros(240, 11, 6, 35),
    "kadhi": Macros(220, 8, 12, 18),
    "mixed vegetable curry": Macros(220, 6, 12, 22),
    "bhindi masala": Macros(180, 4, 12, 14),
    "baingan bharta": Macros(190, 4, 14, 12),
    # Indian mains — non-veg
    "chicken curry": Macros(290, 28, 16, 8),
    "butter chicken": Macros(490, 28, 32, 18),
    "chicken tikka masala": Macros(440, 30, 26, 16),
    "chicken biryani": Macros(520, 30, 18, 60),
    "veg biryani": Macros(450, 12, 12, 70),
    "mutton biryani": Macros(610, 28, 30, 58),
    "egg biryani": Macros(470, 18, 18, 58),
    "prawn biryani": Macros(540, 26, 20, 60),
    "mutton curry": Macros(380, 28, 24, 8),
    "rogan josh": Macros(420, 28, 28, 10),
    "fish curry": Macros(220, 22, 12, 6),
    "prawn curry": Macros(240, 22, 12, 8),
    "egg curry": Macros(260, 14, 18, 8),
    "tandoori chicken": Macros(280, 32, 14, 4),
    "chicken tikka": Macros(240, 30, 12, 4),
    # South Indian
    "masala dosa": Macros(290, 7, 12, 38),
    "plain dosa": Macros(210, 5, 8, 28),
    "rava dosa": Macros(280, 6, 12, 36),
    "idli": Macros(40, 2, 0, 8),  # per piece
    "vada": Macros(130, 4, 7, 14),  # per piece
    "uttapam": Macros(260, 7, 8, 38),
    "sambar": Macros(90, 5, 3, 12),
    "rasam": Macros(60, 3, 2, 8),
    # Breakfast / regional
    "poha": Macros(220, 5, 8, 32),
    "upma": Macros(240, 6, 9, 32),
    "masala omelette": Macros(220, 14, 16, 4),
    "vada pav": Macros(290, 7, 12, 38),
    "pav bhaji": Macros(380, 9, 16, 50),
    "chole bhature": Macros(560, 14, 22, 72),
    "kachori": Macros(180, 4, 10, 18),
    "samosa": Macros(260, 5, 14, 28),  # per piece
    "pakora": Macros(180, 5, 12, 14),
    "spring roll": Macros(140, 3, 8, 14),
    "pani puri": Macros(120, 3, 4, 18),  # per plate
    "bhel puri": Macros(220, 5, 8, 32),
    "dahi puri": Macros(190, 6, 6, 28),
    # Rice / sides
    "jeera rice": Macros(310, 6, 10, 48),
    "plain rice": Macros(260, 5, 1, 56),
    "fried rice": Macros(380, 8, 12, 58),
    "schezwan rice": Macros(420, 9, 14, 62),
    "veg pulao": Macros(340, 7, 10, 54),
    "raita": Macros(80, 4, 4, 6),
    # Indo-Chinese
    "chicken manchurian": Macros(380, 22, 22, 22),
    "gobi manchurian": Macros(320, 6, 18, 32),
    "veg manchurian": Macros(310, 6, 16, 32),
    "hakka noodles": Macros(360, 10, 12, 52),
    "schezwan noodles": Macros(400, 10, 16, 54),
    "chilli chicken": Macros(360, 26, 20, 16),
    "chilli paneer": Macros(360, 18, 22, 20),
    # Pizza / fast food
    "margherita pizza": Macros(270, 12, 10, 32),  # per slice
    "pepperoni pizza": Macros(310, 13, 14, 32),
    "veg pizza": Macros(260, 11, 10, 32),
    # Domino's-style whole-pizza items (typical regular/medium serving)
    "pizza mania": Macros(640, 22, 22, 88),
    "burger pizza": Macros(880, 32, 32, 110),
    "paneer & capsicum": Macros(640, 24, 22, 86),
    "paneer capsicum": Macros(640, 24, 22, 86),
    "capsicum & onion": Macros(580, 18, 18, 86),
    "cheese n tomato": Macros(560, 20, 18, 80),
    "cheese-n-tomato": Macros(560, 20, 18, 80),
    "tomato pizza": Macros(540, 18, 16, 80),
    "farmhouse pizza": Macros(920, 32, 36, 112),
    "farmhouse": Macros(920, 32, 36, 112),
    "deluxe veggie": Macros(900, 32, 34, 110),
    "mexican green wave": Macros(880, 30, 32, 110),
    "peppy paneer": Macros(900, 32, 34, 110),
    "chicken dominator": Macros(1100, 56, 42, 112),
    "chicken golden delight": Macros(1050, 50, 40, 112),
    "non veg supreme": Macros(1080, 54, 42, 110),
    "non-veg supreme": Macros(1080, 54, 42, 110),
    "burger": Macros(350, 16, 16, 36),
    "veg burger": Macros(280, 9, 12, 36),
    "chicken burger": Macros(380, 22, 16, 36),
    "french fries": Macros(320, 4, 16, 40),
    "chicken nuggets": Macros(290, 14, 18, 18),
    "club sandwich": Macros(440, 22, 18, 44),
    "veg sandwich": Macros(280, 8, 10, 38),
    "veg wrap": Macros(320, 9, 12, 42),
    "chicken wrap": Macros(420, 24, 16, 44),
    # Continental
    "pasta arrabiata": Macros(420, 14, 12, 64),
    "pasta carbonara": Macros(580, 22, 28, 58),
    "alfredo pasta": Macros(620, 20, 32, 62),
    "caesar salad": Macros(280, 12, 18, 14),
    # Sweets
    "gulab jamun": Macros(150, 2, 6, 22),  # per piece
    "rasgulla": Macros(110, 3, 1, 22),  # per piece
    "kulfi": Macros(180, 4, 10, 18),
    "vanilla ice cream": Macros(210, 4, 12, 22),
    "chocolate ice cream": Macros(240, 4, 14, 24),
    "brownie": Macros(310, 4, 16, 38),
    # Beverages
    "coke": Macros(140, 0, 0, 35),
    "pepsi": Macros(150, 0, 0, 39),
    "sprite": Macros(140, 0, 0, 35),
    "lassi": Macros(180, 6, 5, 25),
    "mango lassi": Macros(220, 5, 6, 38),
    "buttermilk": Macros(50, 3, 2, 5),
    "lemon soda": Macros(30, 0, 0, 8),
    "iced tea": Macros(90, 0, 0, 22),
    "masala chai": Macros(80, 3, 3, 10),
    "filter coffee": Macros(70, 2, 3, 8),
    "fresh lime water": Macros(40, 0, 0, 10),
    "watermelon juice": Macros(80, 1, 0, 20),
}


# Cheap alias resolution before substring matching.
ALIASES: dict[str, str] = {
    "coca cola": "coke",
    "coca-cola": "coke",
    "thums up": "coke",
    "thumbs up": "coke",
    "limca": "sprite",
    "fanta": "coke",
    "biriyani": "chicken biryani",
    "biryani": "chicken biryani",
    "byriani": "chicken biryani",
    "chiken biryani": "chicken biryani",
    "channa masala": "chana masala",
    "chana": "chana masala",
    "rajmah": "rajma",
    "panner": "paneer butter masala",
    "tandoori": "tandoori chicken",
    "manchurian": "veg manchurian",
    "noodles": "hakka noodles",
    "fries": "french fries",
    "samosas": "samosa",
    "idlis": "idli",
    "vadas": "vada",
    "naans": "naan",
    "rotis": "roti",
    "chapatis": "chapati",
    "chai": "masala chai",
    "tea": "masala chai",
    "coffee": "filter coffee",
    "ice cream": "vanilla ice cream",
}


class Swap(NamedTuple):
    if_present: str  # canonical DB key
    suggestion: str  # user-facing copy
    kcal_saved: int  # positive integer; rendered as a negative delta


# Ordered. Higher-impact swaps first; analyzer returns the first match.
SWAPS: list[Swap] = [
    Swap("chole bhature", "Pick chole with roti instead of bhature — save ~300 kcal", 300),
    Swap("alfredo pasta", "Pick pasta arrabiata — save ~200 kcal & half the fat", 200),
    Swap("pasta carbonara", "Pick pasta arrabiata — save ~160 kcal & less fat", 160),
    Swap("butter naan", "Swap butter naan for roti — save ~200 kcal", 200),
    Swap("cheese naan", "Swap cheese naan for roti — save ~290 kcal", 290),
    Swap("garlic naan", "Swap garlic naan for roti — save ~190 kcal", 190),
    Swap("naan", "Swap naan for roti — save ~160 kcal", 160),
    Swap("paratha", "Swap paratha for roti — save ~180 kcal", 180),
    Swap("aloo paratha", "Swap aloo paratha for roti — save ~230 kcal", 230),
    Swap("schezwan noodles", "Pick hakka noodles — save ~40 kcal & less oil", 40),
    Swap("schezwan rice", "Pick jeera rice — save ~110 kcal", 110),
    Swap("fried rice", "Swap fried rice for jeera rice — save ~70 kcal", 70),
    Swap("butter chicken", "Pick chicken tikka masala — save ~50 kcal & less butter", 50),
    Swap("shahi paneer", "Pick kadai paneer — save ~90 kcal & less cream", 90),
    Swap("paneer butter masala", "Pick kadai paneer — save ~70 kcal & less cream", 70),
    Swap("dal makhani", "Pick dal tadka — save ~140 kcal", 140),
    Swap("french fries", "Skip the fries — save ~320 kcal", 320),
    Swap("coke", "Swap Coke for buttermilk — save ~90 kcal", 90),
    Swap("pepsi", "Swap Pepsi for buttermilk — save ~100 kcal", 100),
    Swap("sprite", "Swap Sprite for lemon soda — save ~110 kcal", 110),
    Swap("mutton biryani", "Pick chicken biryani — save ~90 kcal & less fat", 90),
    Swap("chocolate ice cream", "Pick rasgulla — save ~130 kcal & less fat", 130),
    Swap("vanilla ice cream", "Pick rasgulla — save ~100 kcal & less fat", 100),
]
