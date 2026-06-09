#!/usr/bin/env python3
"""
Apply a language pack to the recipe and food JSON assets.

Usage:
    python3 scripts/i18n/regen.py es
    python3 scripts/i18n/regen.py ca
    python3 scripts/i18n/regen.py fr

Reads:
  - app/src/main/assets/recipes_*.json — recipe corpus
  - app/src/main/assets/foods.json     — ingredient-pedagogy corpus

Merges in:
  - On each recipe:    `name_<lang>`, `steps_<lang>`
  - On each ingredient row: `name_<lang>`
  - On each food entry:     `name_<lang>`, `benefits_<lang>`,
                            `how_to_consume_<lang>`,
                            `when_to_consume_<lang>`,
                            `deficiency_signs_<lang>`,
                            `excess_signs_<lang>`

Translation packs are the canonical source — re-running this script
must produce byte-identical JSON. See CLAUDE.md's seed-data discipline.

The food-translation maps (FOOD_NAMES, FOOD_BENEFITS, ...) are
optional: a pack may omit them while the locale's food pedagogy is
still being translated. Missing entries are reported but do not abort
the run.
"""

from __future__ import annotations

import importlib
import json
import sys
from pathlib import Path

ASSETS = Path(__file__).resolve().parents[2] / "app" / "src" / "main" / "assets"
RECIPE_FILES = ["recipes_blueprint.json", "recipes_longo.json"]
FOODS_FILE = "foods.json"

FOOD_FIELD_MAPS = [
    ("FOOD_NAMES", "name"),
    ("FOOD_BENEFITS", "benefits"),
    ("FOOD_HOW_TO_CONSUME", "how_to_consume"),
    ("FOOD_WHEN_TO_CONSUME", "when_to_consume"),
    ("FOOD_DEFICIENCY_SIGNS", "deficiency_signs"),
    ("FOOD_EXCESS_SIGNS", "excess_signs"),
]


def load_pack(lang: str):
    return importlib.import_module(f"translations_{lang}")


def pack_has_recipes(pack) -> bool:
    """A pack may translate food pedagogy ahead of (or instead of) recipes.

    When none of the recipe maps are present/non-empty, this is a
    food-only pack and recipe processing is skipped rather than reported
    as 53 'missing' recipes. Mirrors the optional-food-maps grace.
    """
    return any(
        getattr(pack, attr, None)
        for attr in ("RECIPE_NAMES", "INGREDIENT_NAMES", "STEPS")
    )


def apply_recipes(lang: str, pack) -> tuple[list[str], list[str], list[str], set[str]]:
    missing_ing: set[str] = set()
    missing_recipe: list[str] = []
    missing_steps: list[str] = []
    extra_steps: list[str] = []

    if not pack_has_recipes(pack):
        return missing_recipe, missing_steps, extra_steps, missing_ing

    for filename in RECIPE_FILES:
        path = ASSETS / filename
        data = json.loads(path.read_text(encoding="utf-8"))
        for r in data["recipes"]:
            rid = r["id"]

            name_tr = pack.RECIPE_NAMES.get(rid)
            if name_tr:
                r[f"name_{lang}"] = name_tr
            else:
                missing_recipe.append(rid)

            steps_tr = pack.STEPS.get(rid)
            en_steps = r.get("steps", [])
            if steps_tr is None:
                if en_steps:
                    missing_steps.append(rid)
            elif len(steps_tr) != len(en_steps):
                extra_steps.append(
                    f"{rid}: EN has {len(en_steps)} steps, {lang} has {len(steps_tr)}"
                )
            else:
                r[f"steps_{lang}"] = steps_tr

            for ing in r["ingredients"]:
                en = ing["name_en"]
                tr = pack.INGREDIENT_NAMES.get(en)
                if tr:
                    ing[f"name_{lang}"] = tr
                else:
                    missing_ing.add(en)

        path.write_text(
            json.dumps(data, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )

    return missing_recipe, missing_steps, extra_steps, missing_ing


def apply_foods(lang: str, pack) -> dict[str, set[str]]:
    """Returns {field_label: set of food_ids missing that field}."""
    path = ASSETS / FOODS_FILE
    if not path.exists():
        return {}
    data = json.loads(path.read_text(encoding="utf-8"))

    missing: dict[str, set[str]] = {label: set() for _, label in FOOD_FIELD_MAPS}

    for food in data["foods"]:
        fid = food["id"]
        for pack_attr, field in FOOD_FIELD_MAPS:
            d = getattr(pack, pack_attr, None)
            if d is None:
                # Pack hasn't started translating this field yet.
                missing[field].add(fid)
                continue
            tr = d.get(fid)
            if tr:
                food[f"{field}_{lang}"] = tr
            else:
                missing[field].add(fid)

    path.write_text(
        json.dumps(data, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    return missing


def report(
    lang: str,
    missing_recipe: list[str],
    missing_steps: list[str],
    extra_steps: list[str],
    missing_ing: set[str],
    missing_food: dict[str, set[str]],
    recipes_skipped: bool = False,
) -> None:
    issues = 0
    if missing_recipe:
        issues += len(missing_recipe)
        print(f"[{lang}] missing recipe names: {len(missing_recipe)}")
        for x in missing_recipe:
            print(f"  - {x}")
    if missing_steps:
        issues += len(missing_steps)
        print(f"[{lang}] missing step translations: {len(missing_steps)}")
        for x in missing_steps:
            print(f"  - {x}")
    if extra_steps:
        issues += len(extra_steps)
        print(f"[{lang}] step-count mismatches: {len(extra_steps)}")
        for x in extra_steps:
            print(f"  - {x}")
    if missing_ing:
        issues += len(missing_ing)
        print(f"[{lang}] missing ingredient translations: {len(missing_ing)}")
        for x in sorted(missing_ing):
            print(f"  - {x!r}")

    # Food translations are non-blocking: we report counts but don't
    # treat them as a failure, so a locale can ship recipe translations
    # ahead of food-pedagogy translations.
    food_warnings = sum(len(s) for s in missing_food.values())
    if food_warnings:
        print(f"[{lang}] foods.json: {food_warnings} translation gap(s)")
        for field, ids in missing_food.items():
            if ids:
                print(f"  - {field}: {len(ids)} missing")

    if issues == 0:
        if recipes_skipped:
            print(f"[{lang}] food-only pack — recipe translations skipped")
        else:
            print(f"[{lang}] recipe translations OK")
    else:
        sys.exit(1)


def main() -> None:
    if len(sys.argv) != 2 or sys.argv[1] not in {"es", "ca", "fr"}:
        print(__doc__)
        sys.exit(2)
    lang = sys.argv[1]
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    pack = load_pack(lang)
    recipes_skipped = not pack_has_recipes(pack)
    missing_recipe, missing_steps, extra_steps, missing_ing = apply_recipes(lang, pack)
    missing_food = apply_foods(lang, pack)
    report(
        lang,
        missing_recipe,
        missing_steps,
        extra_steps,
        missing_ing,
        missing_food,
        recipes_skipped,
    )


if __name__ == "__main__":
    main()
