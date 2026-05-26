#!/usr/bin/env python3
"""
Merge the 6 batch files (drafts/batch-{A..F}.md) and the 2 proof entries
already present in the canonical KB doc into a single ordered set of
145 entries, emitted in catalog order, and rewrite the KB doc.

Run after authoring is complete:
    python3 scripts/foods/merge_entries.py
"""

from __future__ import annotations

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent
KB = Path("/home/mia/Miam/miam-knowledge-base/docs/life/ingredients-pedagogy.md")
CATALOG = json.loads((ROOT / "foods_catalog.json").read_text(encoding="utf-8"))

ENTRY_RE = re.compile(r"^### (\S+)\s*$", re.MULTILINE)


def split_entries(text: str) -> dict[str, str]:
    """Return {food_id: full entry block including the ### heading}."""
    out: dict[str, str] = {}
    positions = [(m.start(), m.group(1)) for m in ENTRY_RE.finditer(text)]
    for i, (start, fid) in enumerate(positions):
        end = positions[i + 1][0] if i + 1 < len(positions) else len(text)
        block = text[start:end].rstrip() + "\n"
        out[fid] = block
    return out


def main() -> int:
    # Source 1: existing KB doc (proof entries olive-oil + garlic live here)
    kb_text = KB.read_text(encoding="utf-8")
    proof_entries = split_entries(kb_text)

    # Source 2: the 6 batches
    batch_entries: dict[str, str] = {}
    for letter in "ABCDEF":
        path = ROOT / "drafts" / f"batch-{letter}.md"
        batch_entries.update(split_entries(path.read_text(encoding="utf-8")))

    # Merge: prefer the proof-entry text for olive-oil + garlic, else batch.
    all_entries: dict[str, str] = {}
    for fid, block in batch_entries.items():
        all_entries[fid] = block
    for fid in ("olive-oil", "garlic"):
        if fid in proof_entries:
            all_entries[fid] = proof_entries[fid]

    # Verify coverage against catalog.
    expected_ids = [f["id"] for f in CATALOG["foods"]]
    missing = [fid for fid in expected_ids if fid not in all_entries]
    extra = [fid for fid in all_entries if fid not in expected_ids]
    if missing or extra:
        print("MISSING:", missing)
        print("EXTRA:", extra)
        return 1

    # Extract front matter from current doc (everything up to and including
    # the "## Entries" heading + its intro paragraph). Replace everything
    # after that with the ordered entries.
    cut = re.search(r"^## Entries\b.*?(?=^### )", kb_text, re.MULTILINE | re.DOTALL)
    if not cut:
        print("Could not locate '## Entries' section header in KB doc.")
        return 1
    front = kb_text[: cut.end()].rstrip() + "\n\n"

    # Rewrite the intro paragraph after "## Entries" — the proof-entry
    # disclaimer is no longer accurate.
    front = re.sub(
        r"(## Entries\b)[\s\S]*",
        r"\1\n\n"
        "All 145 canonical foods, in catalog order. Each entry follows the same five-section "
        "shape established by `olive-oil` and `garlic` at the start of the alliums / oils blocks.\n\n",
        front,
        count=1,
    )

    body = "\n".join(all_entries[fid] for fid in expected_ids)

    KB.write_text(front + body + "\n", encoding="utf-8")
    print(f"Wrote {KB} ({len(all_entries)} entries).")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
