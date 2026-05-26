#!/usr/bin/env python3
"""
Download a representative *free-licensed* image for each food in the
ingredients-pedagogy corpus, bundled into the APK at
`app/src/main/assets/foods/images/<food_id>.jpg`.

Idun is local-first (no runtime network) — these images are baked into
the APK so the Learn screen works fully offline.

Source priority (license-conscious):
  1. Wikimedia Commons direct search (every file on Commons is
     CC-BY / CC-BY-SA / CC0 / PD by policy — always safe).
  2. Wikipedia `pageimages` lead image, but only when its `imageinfo`
     reports a free license (CC*, public-domain, no-restrictions).
     English Wikipedia very occasionally uses non-free "fair use" images
     for food articles — those are skipped, not downloaded.

For every successfully fetched image we record provenance into
`assets/foods/images/_manifest.json` (food_id → source URL, license,
author). The bundled image carries no in-pixel attribution; the UI
shows a generic "Image: Wikimedia Commons" line and this manifest is
the audit trail.

Script is idempotent: existing files are skipped unless --force is
passed. Be polite to Wikimedia: a fixed User-Agent + small sleep
between requests.

Run:
    python3 scripts/foods/download_images.py             # fill in misses
    python3 scripts/foods/download_images.py --force     # re-fetch all
    python3 scripts/foods/download_images.py --dry-run   # don't write
    python3 scripts/foods/download_images.py --only id1,id2
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import time
from pathlib import Path
from urllib.parse import quote
from urllib.request import Request, urlopen
from urllib.error import HTTPError, URLError

ROOT = Path(__file__).resolve().parents[2]
FOODS_JSON = ROOT / "app" / "src" / "main" / "assets" / "foods.json"
OUT_DIR = ROOT / "app" / "src" / "main" / "assets" / "foods" / "images"
MANIFEST = OUT_DIR / "_manifest.json"

USER_AGENT = (
    "Idun-LongevityMealApp/0.2 "
    "(https://github.com/thdelmas/Idun; contact via GitHub issues)"
)
THUMB_PX = 700
SLEEP_BETWEEN_REQUESTS_S = 0.20

WIKI_API = "https://en.wikipedia.org/w/api.php"
COMMONS_API = "https://commons.wikimedia.org/w/api.php"

FREE_LICENSE_PATTERNS = re.compile(
    r"(cc[\- ]?by|cc[\- ]?by[\- ]?sa|cc[\- ]?zero|cc0|public[\- ]?domain|"
    r"pd-|no[\- ]?restrictions|copyrighted free use)",
    re.IGNORECASE,
)


# ---------- HTTP helpers --------------------------------------------------

def http_json(url: str) -> dict:
    req = Request(url, headers={"User-Agent": USER_AGENT, "Accept": "application/json"})
    with urlopen(req, timeout=20) as resp:
        return json.loads(resp.read().decode("utf-8"))


def http_bytes(url: str) -> bytes:
    req = Request(url, headers={"User-Agent": USER_AGENT})
    with urlopen(req, timeout=30) as resp:
        return resp.read()


# ---------- Name cleanup --------------------------------------------------

def clean_title(name: str) -> str:
    """Strip parentheticals and trailing qualifiers so search has a chance."""
    cleaned = re.sub(r"\s*\([^)]*\)\s*", " ", name).strip()
    cleaned = cleaned.split(",")[0].strip()
    # If the name uses a slash to list synonyms ("Lamon / borlotti bean"),
    # the search engine treats the slash as a literal — split and try the
    # last-word reading first ("borlotti bean"), which is usually the
    # canonical English form.
    if "/" in cleaned:
        parts = [p.strip() for p in cleaned.split("/") if p.strip()]
        if parts:
            cleaned = parts[-1]
    return cleaned or name


# ---------- Commons (preferred: every file is free-licensed) -------------

def commons_search_file_title(query: str) -> str | None:
    """Search Commons in the File: namespace, return the top hit's title."""
    url = (
        f"{COMMONS_API}?action=query&format=json&list=search"
        f"&srnamespace=6&srlimit=5&srsearch={quote(query)}"
    )
    try:
        data = http_json(url)
    except (HTTPError, URLError, TimeoutError):
        return None
    for hit in data.get("query", {}).get("search", []):
        title = hit.get("title", "")
        # Filter to image-type files only (skip SVG diagrams, audio, etc.).
        low = title.lower()
        if any(low.endswith(ext) for ext in (".jpg", ".jpeg", ".png", ".webp")):
            return title
    return None


def commons_imageinfo(file_title: str) -> dict | None:
    """Fetch thumbnail URL + license metadata for a Commons File: page."""
    url = (
        f"{COMMONS_API}?action=query&format=json&prop=imageinfo"
        f"&iiprop=url|extmetadata&iiurlwidth={THUMB_PX}"
        f"&titles={quote(file_title)}"
    )
    try:
        data = http_json(url)
    except (HTTPError, URLError, TimeoutError):
        return None
    for _, page in data.get("query", {}).get("pages", {}).items():
        infos = page.get("imageinfo", [])
        if infos:
            return infos[0]
    return None


def extract_license(info: dict) -> tuple[str | None, str | None]:
    """Return (license_short_name, author_html-stripped) from imageinfo."""
    meta = info.get("extmetadata", {})
    license_name = (meta.get("LicenseShortName") or {}).get("value")
    author_raw = (meta.get("Artist") or {}).get("value", "")
    # Strip HTML tags from the author field — it's often a rich-text blob.
    author = re.sub(r"<[^>]+>", "", author_raw).strip() or None
    return license_name, author


# ---------- Wikipedia (fallback: must verify license) --------------------

def wikipedia_lead_file(title: str) -> str | None:
    """Get the `File:...` title of the lead image for a Wikipedia article."""
    url = (
        f"{WIKI_API}?action=query&format=json&prop=pageimages"
        f"&piprop=name&redirects=1&titles={quote(title)}"
    )
    try:
        data = http_json(url)
    except (HTTPError, URLError, TimeoutError):
        return None
    for _, page in data.get("query", {}).get("pages", {}).items():
        name = page.get("pageimage")
        if name:
            return f"File:{name}"
    return None


def wikipedia_opensearch(query: str) -> str | None:
    url = (
        f"{WIKI_API}?action=opensearch&format=json&limit=1&search={quote(query)}"
    )
    try:
        data = http_json(url)
    except (HTTPError, URLError, TimeoutError):
        return None
    if isinstance(data, list) and len(data) >= 2 and data[1]:
        return data[1][0]
    return None


# ---------- Main resolution -----------------------------------------------

def resolve_image(name: str) -> dict | None:
    """Return {url, file_title, license, author, source} or None."""
    base = clean_title(name)

    # 1. Commons direct search.
    file_title = commons_search_file_title(base)
    if file_title:
        time.sleep(SLEEP_BETWEEN_REQUESTS_S)
        info = commons_imageinfo(file_title)
        if info:
            license_name, author = extract_license(info)
            thumb = info.get("thumburl") or info.get("url")
            if thumb:
                return {
                    "url": thumb,
                    "file_title": file_title,
                    "license": license_name,
                    "author": author,
                    "source": "commons-search",
                }

    # 2. Wikipedia lead image (must verify license).
    file_title = wikipedia_lead_file(base)
    if not file_title:
        alt = wikipedia_opensearch(base)
        if alt:
            time.sleep(SLEEP_BETWEEN_REQUESTS_S)
            file_title = wikipedia_lead_file(alt)

    if file_title:
        time.sleep(SLEEP_BETWEEN_REQUESTS_S)
        info = commons_imageinfo(file_title)  # Commons is the canonical store
        if info:
            license_name, author = extract_license(info)
            if license_name and FREE_LICENSE_PATTERNS.search(license_name):
                thumb = info.get("thumburl") or info.get("url")
                if thumb:
                    return {
                        "url": thumb,
                        "file_title": file_title,
                        "license": license_name,
                        "author": author,
                        "source": "wikipedia-lead",
                    }

    return None


# ---------- Driver --------------------------------------------------------

def load_manifest() -> dict:
    if MANIFEST.exists():
        try:
            return json.loads(MANIFEST.read_text(encoding="utf-8"))
        except json.JSONDecodeError:
            pass
    return {}


def save_manifest(data: dict) -> None:
    MANIFEST.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--force", action="store_true",
                        help="Re-fetch even if the file already exists.")
    parser.add_argument("--dry-run", action="store_true",
                        help="Resolve URLs but don't write files or manifest.")
    parser.add_argument("--only", default=None,
                        help="Comma-separated food IDs to process (testing).")
    args = parser.parse_args()

    foods = json.loads(FOODS_JSON.read_text(encoding="utf-8"))["foods"]
    if args.only:
        wanted = {s.strip() for s in args.only.split(",") if s.strip()}
        foods = [f for f in foods if f["id"] in wanted]
        if not foods:
            print(f"No foods matched --only={args.only!r}", file=sys.stderr)
            return 2

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    manifest = load_manifest()

    hits, misses, skipped = [], [], []
    for i, food in enumerate(foods, 1):
        fid, name = food["id"], food["name_en"]
        out_path = OUT_DIR / f"{fid}.jpg"
        prefix = f"[{i:3d}/{len(foods)}] {fid:35s}"

        if out_path.exists() and not args.force:
            print(f"{prefix} SKIP (exists)")
            skipped.append(fid)
            continue

        resolved = resolve_image(name)
        if not resolved:
            print(f"{prefix} MISS  ({name!r})")
            misses.append((fid, name))
            continue

        url = resolved["url"]
        if args.dry_run:
            print(f"{prefix} DRY   [{resolved['source']}/{resolved.get('license')}] {url}")
            hits.append(fid)
            continue

        try:
            blob = http_bytes(url)
            out_path.write_bytes(blob)
            manifest[fid] = {
                "source_url": url,
                "file_title": resolved["file_title"],
                "license": resolved.get("license"),
                "author": resolved.get("author"),
                "resolved_via": resolved["source"],
            }
            save_manifest(manifest)
            print(f"{prefix} OK    {len(blob) // 1024} KB  [{resolved.get('license') or 'unknown'}]")
            hits.append(fid)
        except (HTTPError, URLError, TimeoutError) as e:
            print(f"{prefix} ERR   {e}")
            misses.append((fid, name))
        time.sleep(SLEEP_BETWEEN_REQUESTS_S)

    print()
    print(f"Hits:    {len(hits)}")
    print(f"Skipped: {len(skipped)}")
    print(f"Misses:  {len(misses)}")
    if misses:
        print("\nFoods with no matching free-licensed image:")
        for fid, name in misses:
            print(f"  - {fid}  ({name})")
    return 0


if __name__ == "__main__":
    sys.exit(main())
