#!/usr/bin/env python3
"""
Extract the 145 canonical food entries from the KB markdown into a JSON
asset the Android app reads at runtime.

Reads:
    /home/mia/Miam/miam-knowledge-base/docs/life/ingredients-pedagogy.md

Writes:
    app/src/main/assets/foods.json

Each food entry in the JSON carries the canonical English text for all
five teaching blocks; localised counterparts (`*_es`, `*_ca`, `*_fr`)
are merged in by scripts/i18n/regen.py per the same pattern as recipes.

The KB doc is source of truth — never edit foods.json directly.
"""

from __future__ import annotations

import json
import re
import sys
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
KB = Path("/home/mia/Miam/miam-knowledge-base/docs/life/ingredients-pedagogy.md")
CATALOG = json.loads((ROOT / "scripts" / "foods" / "foods_catalog.json").read_text(encoding="utf-8"))
OUT = ROOT / "app" / "src" / "main" / "assets" / "foods.json"

SECTIONS = [
    ("benefits", "Benefits"),
    ("how_to_consume", "How to consume"),
    ("when_to_consume", "When to consume"),
    ("deficiency_signs", "Deficiency signs"),
    ("excess_signs", "Excess signs"),
]


def extract_block(entry_text: str, label: str) -> str:
    """Pull the paragraph following a `**Label.**` marker, up to the next
    bold-label marker or the end of the entry."""
    pat = re.compile(
        rf"\*\*{re.escape(label)}\.\*\*\s*(.+?)(?=\n\*\*[A-Z][^*]*\.\*\*|\Z)",
        re.DOTALL,
    )
    m = pat.search(entry_text)
    if not m:
        raise ValueError(f"missing '{label}' block")
    return m.group(1).strip()


def main() -> int:
    text = KB.read_text(encoding="utf-8")
    expected_ids = [f["id"] for f in CATALOG["foods"]]
    id_to_meta = {f["id"]: f for f in CATALOG["foods"]}

    # Split entries.
    blocks: dict[str, str] = {}
    positions = list(re.finditer(r"^### (\S+)\s*$", text, re.MULTILINE))
    for i, m in enumerate(positions):
        fid = m.group(1)
        if fid not in id_to_meta:
            continue  # skip non-food H3s (catalog subheadings)
        start = m.end()
        end = positions[i + 1].start() if i + 1 < len(positions) else len(text)
        blocks[fid] = text[start:end].strip()

    foods = []
    errors = []
    for fid in expected_ids:
        if fid not in blocks:
            errors.append(f"{fid}: missing entry block")
            continue
        entry = blocks[fid]
        meta = id_to_meta[fid]
        try:
            food = {
                "id": fid,
                "category": meta["category"],
                "name_en": meta["name_en"],
            }
            for key, label in SECTIONS:
                food[f"{key}_en"] = extract_block(entry, label)
            foods.append(food)
        except ValueError as e:
            errors.append(f"{fid}: {e}")

    if errors:
        print("EXTRACTION ERRORS:")
        for e in errors:
            print(f"  {e}")
        return 1

    out = {
        "_meta": {
            "source_doc": "Miam/miam-knowledge-base/docs/life/ingredients-pedagogy.md",
            "generated": date.today().isoformat(),
            "food_count": len(foods),
        },
        "foods": foods,
    }
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(out, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"Wrote {OUT} ({len(foods)} foods).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
