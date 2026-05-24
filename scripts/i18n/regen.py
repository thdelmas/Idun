#!/usr/bin/env python3
"""
Apply a language pack to the recipe JSON assets.

Usage:
    python3 scripts/i18n/regen.py es
    python3 scripts/i18n/regen.py ca
    python3 scripts/i18n/regen.py fr

Reads app/src/main/assets/recipes_*.json, merges in `name_<lang>` /
`steps_<lang>` on each recipe and `name_<lang>` on each ingredient from
the matching translations_<lang> module, and writes the files back in
place.

Translations are the canonical source — re-running this script must
produce byte-identical JSON. See CLAUDE.md's seed-data discipline.
"""

from __future__ import annotations

import importlib
import json
import sys
from pathlib import Path

ASSETS = Path(__file__).resolve().parents[2] / "app" / "src" / "main" / "assets"
RECIPE_FILES = ["recipes_blueprint.json", "recipes_longo.json"]


def load_pack(lang: str):
    return importlib.import_module(f"translations_{lang}")


def apply(lang: str, pack) -> None:
    missing_ing: set[str] = set()
    missing_recipe: list[str] = []
    missing_steps: list[str] = []
    extra_steps: list[str] = []

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

    report(lang, missing_recipe, missing_steps, extra_steps, missing_ing)


def report(lang, missing_recipe, missing_steps, extra_steps, missing_ing) -> None:
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
    if issues == 0:
        print(f"[{lang}] OK — all translations applied")
    else:
        sys.exit(1)


def main() -> None:
    if len(sys.argv) != 2 or sys.argv[1] not in {"es", "ca", "fr"}:
        print(__doc__)
        sys.exit(2)
    lang = sys.argv[1]
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    pack = load_pack(lang)
    apply(lang, pack)


if __name__ == "__main__":
    main()
