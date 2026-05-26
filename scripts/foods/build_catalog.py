#!/usr/bin/env python3
"""
Build the canonical foods catalog from the recipe ingredient lines.

The 466 per-recipe ingredient lines (across recipes_blueprint.json and
recipes_longo.json) collapse to ~100 canonical foods. This script:

  1. Defines the canonical catalog (food_id, display name, category,
     regex patterns that match raw ingredient strings).
  2. Walks every recipe ingredient line and assigns it a food_id.
  3. Reports unmatched lines (must reach zero before the catalog is
     accepted as canonical).
  4. Prints / writes summary artifacts.

Usage:
    python3 scripts/foods/build_catalog.py            # report only
    python3 scripts/foods/build_catalog.py --write    # also write
                                                       # foods_catalog.json
"""

from __future__ import annotations

import json
import re
import sys
from collections import defaultdict
from pathlib import Path

ASSETS = Path(__file__).resolve().parents[2] / "app" / "src" / "main" / "assets"
OUT = ASSETS / "foods_catalog.json"
RECIPE_FILES = ["recipes_blueprint.json", "recipes_longo.json"]


# Canonical foods catalog.
# Order matters: the first matching pattern wins, so list more specific
# foods before broader catch-alls (e.g. "spring onion" before "onion").
#
# Each entry: (food_id, display_en, category, [regex patterns]).
CATALOG: list[tuple[str, str, str, list[str]]] = [
    # ---------------- SPECIFIC PRE-MATCH (powders / pastes / sauces / flours
    # whose name contains a broader food token; must win over the generic
    # produce entry below) ----------------
    ("garlic-powder", "Garlic powder", "SPICES", [r"garlic powder"]),
    ("onion-powder", "Onion powder", "SPICES", [r"onion powder"]),
    ("ginger-powder", "Ginger (powder)", "SPICES", [r"ginger powder"]),
    ("flour-chickpea", "Chickpea flour", "FLOURS_BREAD", [r"chickpea flour"]),
    ("flour-almond", "Almond flour", "FLOURS_BREAD", [r"almond flour"]),
    ("tomato-paste", "Tomato paste", "PANTRY_SWEETS", [r"tomato paste"]),
    ("tomato-sauce", "Tomato sauce (jarred)", "PANTRY_SWEETS", [r"tomato sauce"]),
    ("hummus-sundried", "Sun-dried tomato hummus", "PANTRY_SWEETS", [r"sun-dried tomato hummus"]),
    ("cavolo-nero", "Cavolo nero (Tuscan kale)", "PRODUCE", [r"cavolo nero"]),
    ("sweet-potato", "Sweet potato", "PRODUCE", [r"sweet potato"]),
    ("milk-almond", "Almond milk", "MILKS_BROTHS", [r"almond milk"]),
    ("milk-macadamia", "Macadamia nut milk", "MILKS_BROTHS", [r"macadamia nut milk"]),
    ("macadamia-nut", "Macadamia nut", "NUTS_SEEDS", [r"macadamia nut"]),

    # ---------------- PRODUCE: alliums ----------------
    ("spring-onion", "Spring onion", "PRODUCE", [r"\bspring onion\b", r"\bfresh onion\b"]),
    ("shallot", "Shallot", "PRODUCE", [r"\bshallot"]),
    ("leek", "Leek", "PRODUCE", [r"\bleek"]),
    ("garlic", "Garlic", "PRODUCE", [r"\bgarlic\b", r"\bgarlic clove"]),
    ("chive", "Chive bulb", "PRODUCE", [r"\bchive bulb"]),
    ("chives", "Chives (herb)", "HERBS", [r"\bchives\b"]),
    ("onion", "Onion", "PRODUCE", [r"\bonion"]),

    # ---------------- PRODUCE: cruciferous ----------------
    ("broccoli", "Broccoli", "PRODUCE", [r"\bbroccoli"]),
    ("cauliflower", "Cauliflower", "PRODUCE", [r"cauliflower", r"riced cauliflower"]),
    ("cabbage", "Cabbage", "PRODUCE", [r"\bcabbage", r"red cabbage", r"white cabbage"]),
    ("radish", "Radish", "PRODUCE", [r"\bradish"]),

    # ---------------- PRODUCE: roots / starches ----------------
    ("carrot", "Carrot", "PRODUCE", [r"\bcarrot"]),
    ("celery", "Celery", "PRODUCE", [r"\bcelery"]),
    ("potato", "Potato", "PRODUCE", [r"\bpotato(es)?\b"]),
    ("butternut-squash", "Butternut squash", "PRODUCE", [r"butternut squash"]),
    ("acorn-squash", "Acorn squash", "PRODUCE", [r"acorn squash"]),
    ("spaghetti-squash", "Spaghetti squash", "PRODUCE", [r"spaghetti squash"]),
    ("pumpkin", "Pumpkin", "PRODUCE", [r"\bpumpkin"]),

    # ---------------- PRODUCE: nightshades / fruiting vegs ----------------
    ("tomato", "Tomato", "PRODUCE", [r"tomato(es)?", r"cherry tomato", r"roma tomato", r"sauce tomato", r"salad tomato", r"firm tomato", r"small tomato", r"peeled tomato"]),
    ("bell-pepper", "Bell pepper", "PRODUCE", [r"bell pepper", r"red peppers?\b", r"yellow peppers?\b", r"large pepper", r"large yellow peppers?\b", r"roasted bell pepper"]),
    ("eggplant", "Eggplant / aubergine", "PRODUCE", [r"\beggplant"]),
    ("zucchini", "Zucchini / courgette", "PRODUCE", [r"\bzucchini"]),
    ("cucumber", "Cucumber", "PRODUCE", [r"\bcucumber"]),

    # ---------------- PRODUCE: leafy / bitter ----------------
    ("spinach", "Spinach", "PRODUCE", [r"\bspinach"]),
    ("nettle", "Nettle", "PRODUCE", [r"\bnettle"]),
    ("escarole-endive", "Escarole / endive", "PRODUCE", [r"escarole", r"\bendive"]),
    ("radicchio", "Radicchio", "PRODUCE", [r"\bradicchio"]),

    # ---------------- PRODUCE: mushrooms ----------------
    ("mushroom", "Mushroom", "PRODUCE", [r"\bmushroom", r"cremini mushroom", r"shiitake mushroom", r"porcini mushroom", r"oyster mushroom"]),

    # ---------------- PRODUCE: pulses (fresh) ----------------
    ("fava-bean", "Fava bean (broad bean)", "PRODUCE", [r"\bfava bean"]),
    ("lamon-bean", "Lamon / borlotti bean", "PRODUCE", [r"lamon bean"]),
    ("green-bean", "Green bean", "PRODUCE", [r"green bean", r"green string bean"]),
    ("mung-bean-sprout", "Mung bean sprout", "PRODUCE", [r"mung bean sprout"]),
    ("sugar-snap-pea", "Sugar snap pea", "PRODUCE", [r"sugar snap pea"]),

    # ---------------- PRODUCE: artichoke ----------------
    ("artichoke", "Artichoke", "PRODUCE", [r"\bartichoke"]),

    # ---------------- FRUIT ----------------
    ("apple", "Apple", "FRUIT", [r"\bapple"]),
    ("pear", "Pear", "FRUIT", [r"\bpear"]),
    ("lemon", "Lemon", "FRUIT", [r"\blemon"]),
    ("lime", "Lime", "FRUIT", [r"\blime"]),
    ("orange", "Orange", "FRUIT", [r"\borange"]),
    ("blueberry", "Blueberry", "FRUIT", [r"blueberr"]),
    ("strawberry", "Strawberry", "FRUIT", [r"strawberr"]),
    ("blackcurrant", "Blackcurrant", "FRUIT", [r"black currant"]),
    ("cherry-fresh", "Cherry", "FRUIT", [r"dark cherr", r"\bcherr"]),
    ("fig-dried", "Fig (dried)", "FRUIT", [r"dried fig"]),
    ("pomegranate", "Pomegranate", "FRUIT", [r"pomegranate"]),
    ("watermelon", "Watermelon", "FRUIT", [r"watermelon"]),
    ("raisin", "Raisin", "FRUIT", [r"\braisin"]),
    ("cranberry-dried", "Cranberry (dried)", "FRUIT", [r"cranberr"]),
    ("berries-mixed", "Mixed berries", "FRUIT", [r"fresh berries"]),

    # ---------------- HERBS ----------------
    ("basil", "Basil", "HERBS", [r"\bbasil"]),
    ("parsley", "Parsley", "HERBS", [r"\bparsley"]),
    ("cilantro", "Cilantro / fresh coriander", "HERBS", [r"\bcilantro"]),
    ("mint", "Mint", "HERBS", [r"\bmint"]),
    ("rosemary-fresh", "Rosemary (fresh)", "HERBS", [r"^rosemary$", r"^(?!dried).*\brosemary\b"]),
    ("sage", "Sage", "HERBS", [r"\bsage"]),
    ("herb-mix", "Mixed culinary herbs", "HERBS", [r"aromatic herbs mix", r"mixed herbs"]),

    # ---------------- LEGUMES / GRAINS / PASTA ----------------
    ("chickpea", "Chickpea", "LEGUMES_GRAINS_PASTA", [r"\bchickpea"]),
    ("lentil", "Lentil", "LEGUMES_GRAINS_PASTA", [r"\blentil"]),
    ("black-bean", "Black bean", "LEGUMES_GRAINS_PASTA", [r"black bean"]),
    ("dried-bean-mixed", "Dried beans (mixed / cannellini-borlotti)", "LEGUMES_GRAINS_PASTA", [r"^dry beans$"]),
    ("chestnut-dried", "Dried chestnut", "LEGUMES_GRAINS_PASTA", [r"dry chestnut"]),
    ("spelt", "Spelt", "LEGUMES_GRAINS_PASTA", [r"\bspelt"]),
    ("rice-brown", "Brown / semi-brown rice", "LEGUMES_GRAINS_PASTA", [r"semi-brown rice"]),
    ("pasta-wheat", "Wheat pasta", "LEGUMES_GRAINS_PASTA", [r"\blinguine", r"rigatoni", r"maccheroni", r"tagliolini", r"ditalini"]),

    # ---------------- FLOURS / BREAD ----------------
    ("flour-wheat", "Wheat flour (00 / soft / whole)", "FLOURS_BREAD", [r"wheat flour", r"italian 00 wheat flour", r"soft wheat flour"]),
    ("flour-corn", "Corn / maize flour", "FLOURS_BREAD", [r"corn meal", r"maize flour", r"thin corn flour"]),
    ("corn-starch", "Corn starch", "FLOURS_BREAD", [r"corn starch"]),
    ("semolina", "Durum-wheat semolina", "FLOURS_BREAD", [r"semolina"]),
    ("bread", "Bread / breadcrumbs", "FLOURS_BREAD", [r"sandwich bread", r"stale bread", r"toasted homemade bread", r"breadcrumb"]),
    ("taralli", "Taralli (Pugliese cracker)", "FLOURS_BREAD", [r"\btaralli"]),

    # ---------------- MILKS / BROTHS ----------------
    ("milk-coconut", "Coconut milk", "MILKS_BROTHS", [r"coconut milk"]),
    ("milk-plant", "Plant-based milk (generic)", "MILKS_BROTHS", [r"plant-based milk"]),
    ("broth-vegetable", "Vegetable broth / stock", "MILKS_BROTHS", [r"vegetable broth", r"vegetable stock", r"water or vegetable broth"]),

    # ---------------- DAIRY / EGGS ----------------
    ("egg", "Egg", "DAIRY_EGGS", [r"\begg"]),
    ("cheese-parmesan", "Aged parmesan (Grana Padano / Parmigiano)", "DAIRY_EGGS", [r"grana padano", r"parmigiano reggiano"]),
    ("cheese-ricotta-salata", "Ricotta salata", "DAIRY_EGGS", [r"ricotta salata"]),

    # ---------------- NUTS / SEEDS ----------------
    ("almond", "Almond", "NUTS_SEEDS", [r"\balmond\b", r"peeled almond", r"roasted almond", r"shelled almond", r"shelled unpeeled almond"]),
    ("hazelnut", "Hazelnut", "NUTS_SEEDS", [r"hazelnut"]),
    ("walnut", "Walnut", "NUTS_SEEDS", [r"walnut"]),
    ("pine-nut", "Pine nut", "NUTS_SEEDS", [r"pine nut"]),
    ("pistachio", "Pistachio", "NUTS_SEEDS", [r"pistachio"]),
    ("chia-seed", "Chia seed", "NUTS_SEEDS", [r"chia seed"]),
    ("flax-seed", "Flax seed", "NUTS_SEEDS", [r"flax seed", r"ground flax"]),
    ("hemp-seed", "Hemp seed", "NUTS_SEEDS", [r"hemp seed"]),

    # ---------------- OILS / VINEGARS / WINE ----------------
    ("olive-oil", "Olive oil (extra virgin)", "OILS_VINEGARS_WINE", [r"olive oil", r"extra virgin olive oil"]),
    ("sunflower-oil", "Sunflower oil", "OILS_VINEGARS_WINE", [r"sunflower oil"]),
    ("vinegar-balsamic", "Balsamic vinegar", "OILS_VINEGARS_WINE", [r"balsamic vinegar"]),
    ("vinegar-red-wine", "Red wine vinegar", "OILS_VINEGARS_WINE", [r"red wine vinegar"]),
    ("vinegar-white-wine", "White wine vinegar", "OILS_VINEGARS_WINE", [r"white wine vinegar"]),
    ("wine-cooking", "Cooking wine", "OILS_VINEGARS_WINE", [r"red wine", r"dry white wine", r"^wine$"]),

    # ---------------- SEAFOOD ----------------
    ("anchovy", "Anchovy", "SEAFOOD", [r"anchov"]),
    ("sardine", "Sardine", "SEAFOOD", [r"\bsardine"]),
    ("octopus", "Octopus", "SEAFOOD", [r"\boctopus"]),
    ("squid-cuttlefish", "Squid / cuttlefish", "SEAFOOD", [r"\bsquid", r"cuttlefish"]),
    ("snapper", "Snapper", "SEAFOOD", [r"\bsnapper"]),
    ("fish-stew-mix", "Mixed fish (for stew)", "SEAFOOD", [r"mixed fish for stew"]),

    # ---------------- PANTRY / SWEETS ----------------
    ("olive-table", "Table olive", "PANTRY_SWEETS", [r"\bolive"]),
    ("caper", "Caper", "PANTRY_SWEETS", [r"\bcaper"]),
    ("chocolate-dark", "Dark chocolate / cacao", "PANTRY_SWEETS", [r"dark chocolate", r"blueprint cacao", r"\bcacao"]),
    ("mustard-dijon", "Dijon mustard", "PANTRY_SWEETS", [r"dijon mustard"]),
    ("tamari", "Tamari / soy sauce", "PANTRY_SWEETS", [r"tamari"]),
    ("honey", "Honey", "PANTRY_SWEETS", [r"\bhoney"]),
    ("maple-syrup", "Maple syrup", "PANTRY_SWEETS", [r"maple syrup"]),
    ("sugar-cane", "Cane / refined sugar", "PANTRY_SWEETS", [r"natural cane sugar", r"powdered sugar", r"^sugar$"]),
    ("nutritional-yeast", "Nutritional yeast", "PANTRY_SWEETS", [r"nutritional yeast"]),
    ("tahini", "Tahini (sesame paste)", "PANTRY_SWEETS", [r"tahini"]),
    ("liquid-smoke", "Liquid smoke", "PANTRY_SWEETS", [r"liquid smoke"]),
    ("umami-seasoning", "Umami seasoning", "PANTRY_SWEETS", [r"umami seasoning"]),
    ("ketchup-unsweetened", "Ketchup (unsweetened)", "PANTRY_SWEETS", [r"unsweetened ketchup"]),
    ("pepper-pickled", "Pickled / vinegar-preserved pepper", "PANTRY_SWEETS", [r"vinegar-preserved pepper"]),

    # ---------------- SPICES ----------------
    ("salt", "Salt", "SPICES", [r"\bsalt\b", r"kosher salt"]),
    ("pepper-black", "Black pepper", "SPICES", [r"black pepper", r"^pepper$"]),
    ("bay-leaf", "Bay leaf", "SPICES", [r"bay leaf", r"bay leaves"]),
    ("cinnamon", "Cinnamon", "SPICES", [r"\bcinnamon"]),
    ("cumin", "Cumin", "SPICES", [r"\bcumin"]),
    ("coriander-seed", "Coriander (ground)", "SPICES", [r"coriander powder"]),
    ("cardamom", "Cardamom", "SPICES", [r"\bcardamom"]),
    ("nutmeg", "Nutmeg", "SPICES", [r"\bnutmeg"]),
    ("clove-spice", "Clove (whole)", "SPICES", [r"whole cloves"]),
    ("turmeric", "Turmeric", "SPICES", [r"\bturmeric"]),
    ("paprika", "Paprika (incl. smoked)", "SPICES", [r"\bpaprika"]),
    ("chili", "Chili / cayenne / peperoncino", "SPICES", [r"chili", r"peperoncino", r"red pepper flakes", r"dry red chili"]),
    ("chipotle", "Chipotle (smoked)", "SPICES", [r"chipotle"]),
    ("oregano", "Oregano", "SPICES", [r"\boregano"]),
    ("thyme-dried", "Thyme (dried)", "SPICES", [r"dried thyme"]),
    ("rosemary-dried", "Rosemary (dried)", "SPICES", [r"dried rosemary"]),
    ("italian-seasoning", "Italian seasoning blend", "SPICES", [r"italian seasoning"]),
    ("garam-masala", "Garam masala", "SPICES", [r"garam masala"]),
    ("vanilla-extract", "Vanilla extract", "SPICES", [r"vanilla extract"]),
    ("zaatar", "Za'atar", "SPICES", [r"za'atar"]),
    ("butterfly-pea-flower", "Butterfly pea flower (powder)", "SPICES", [r"butterfly pea flower"]),

    # ---------------- WATER ----------------
    ("water", "Water", "OTHER", [r"^water$"]),
]


def compile_catalog():
    compiled = []
    for fid, name, cat, patterns in CATALOG:
        compiled.append((fid, name, cat, [re.compile(p, re.IGNORECASE) for p in patterns]))
    return compiled


def match_food(line: str, compiled) -> str | None:
    n = line.lower().strip()
    for fid, _name, _cat, patterns in compiled:
        for pat in patterns:
            if pat.search(n):
                return fid
    return None


def main(write: bool) -> int:
    compiled = compile_catalog()
    by_food: dict[str, list[str]] = defaultdict(list)
    unmatched: list[tuple[str, str]] = []  # (recipe_id, ingredient line)

    for filename in RECIPE_FILES:
        data = json.loads((ASSETS / filename).read_text(encoding="utf-8"))
        for r in data["recipes"]:
            for ing in r["ingredients"]:
                name = ing["name_en"]
                fid = match_food(name, compiled)
                if fid:
                    by_food[fid].append(name)
                else:
                    unmatched.append((r["id"], name))

    # Report
    print(f"Catalog entries: {len(CATALOG)}")
    print(f"Matched ingredient lines: {sum(len(v) for v in by_food.values())}")
    print(f"Unmatched ingredient lines: {len(unmatched)}")
    if unmatched:
        print("\n-- UNMATCHED --")
        for rid, name in unmatched:
            print(f"  [{rid}] {name}")

    unused = [fid for fid, _n, _c, _p in CATALOG if fid not in by_food]
    if unused:
        print(f"\n-- UNUSED catalog entries ({len(unused)}) --")
        for fid in unused:
            print(f"  {fid}")

    if write:
        out = {
            "_meta": {
                "source_doc": "Miam/miam-knowledge-base/docs/life/ingredients-pedagogy.md",
                "generated_by": "scripts/foods/build_catalog.py",
                "notes": (
                    "Bundled as an Android asset and loaded by FoodResolver "
                    "at runtime to map a recipe ingredient line to a food_id. "
                    "Patterns are case-insensitive Python/Java-compatible regex; "
                    "order matters (first match wins)."
                ),
            },
            "foods": [
                {"id": fid, "name_en": name, "category": cat, "patterns": patterns}
                for fid, name, cat, patterns in CATALOG
            ],
        }
        OUT.write_text(json.dumps(out, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        print(f"\nWrote {OUT}")

    return 1 if unmatched else 0


if __name__ == "__main__":
    write = "--write" in sys.argv[1:]
    sys.exit(main(write))
