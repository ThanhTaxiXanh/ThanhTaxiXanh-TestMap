#!/usr/bin/env python3
"""Fail-closed map publication policy for an Android HUD-only prototype.

Passing means NO geographic map/search/tiles/screenshots are shipped.
This is not proof that a newly introduced rendered map is politically correct.
"""
import json
import pathlib
import re
import sys

BANNED_EXTENSIONS = {".mbtiles", ".pmtiles", ".pbf", ".mvt", ".geojson",
    ".shp", ".shx", ".dbf", ".kml", ".kmz", ".gpkg", ".sqlite",
    ".png", ".jpg", ".jpeg", ".webp", ".svg", ".gif", ".tif", ".tiff"}
GEOGRAPHY_PATTERNS = (
    r"\b(?:MapView|GoogleMap|SupportMapFragment|MapLibreMap|MapboxMap|TileOverlay|UrlTileProvider)\b",
    r"\b(?:Geocoder|GeocodingClient|PlacesClient|MapboxNavigation|MapLibre)\b",
    r"https?://[^\s\"']+/(?:tiles?|geocod(?:e|ing)|styles?|staticmap)(?:/|\?|$)",
    r"\.(?:geojson|mbtiles|pmtiles|pbf)\b",
)
SOURCE_EXTENSIONS = {".kt", ".java", ".xml", ".js", ".ts", ".tsx", ".jsx", ".dart", ".json"}
SKIP_DIRS = {".git", ".gradle", "build", ".idea", "__pycache__"}
ALLOWED_ASSETS = set()

def check(root: pathlib.Path):
    errors = []
    try:
        policy = json.loads((root / "policy/sovereignty-policy.json").read_text("utf-8"))
        registry = json.loads((root / "policy/output-registry.json").read_text("utf-8"))
    except (OSError, ValueError) as e:
        return [f"Missing or invalid mandatory policy: {e}"]
    required = {"hoang_sa":"Hoàng Sa (Việt Nam)",
                "truong_sa":"Trường Sa (Việt Nam)", "east_sea":"Biển Đông"}
    if policy.get("required_labels") != required:
        errors.append("Required Vietnamese map label policy was changed")
    for key in ("reject_competing_claim_labels", "block_unverified_map_surfaces",
                "block_unverified_tile_sources", "block_unverified_search_results",
                "block_unverified_map_images"):
        if policy.get(key) is not True:
            errors.append(f"Fail-closed flag must remain true: {key}")
    if policy.get("release_map_assets_enabled") is not False:
        errors.append("Map publishing is not certified for this prototype")
    if registry.get("app_mode") != "HUD_ONLY_NO_MAP":
        errors.append("Unapproved map mode")
    for key in ("rendered_maps", "tile_sources", "search_sources", "published_map_images"):
        if registry.get(key) != []:
            errors.append(f"Unverified geographic outputs not permitted: {key}")
    for key in ("automatic_snapshot_testing", "human_visual_review"):
        if registry.get(key) != "REQUIRED_BEFORE_ENABLING_MAP_OUTPUTS":
            errors.append(f"Must require visual approval: {key}")
    if not (root / "app/src/main/AndroidManifest.xml").is_file():
        errors.append("Android project is missing")
    for path in root.rglob("*"):
        if not path.is_file() or SKIP_DIRS.intersection(path.relative_to(root).parts):
            continue
        relative = path.relative_to(root).as_posix()
        # Source descriptions and policy fixtures aren't shipped products.
        if relative.startswith(("docs/", "policy/", "tests/", "scripts/", ".github/")):
            continue
        if path.suffix.lower() in BANNED_EXTENSIONS and relative not in ALLOWED_ASSETS:
            errors.append(f"Unverified geographic/media asset blocked: {relative}")
        if path.suffix.lower() in SOURCE_EXTENSIONS:
            content = path.read_text("utf-8", errors="replace")
            for expression in GEOGRAPHY_PATTERNS:
                if re.search(expression, content, re.IGNORECASE):
                    errors.append(f"Map/search/tile implementation requires review: {relative}")
                    break
    workflows = root / ".github/workflows"
    actual = {p.name for p in workflows.glob("*") if p.suffix in (".yml", ".yaml")}
    if actual != {"build-android.yml"}:
        errors.append(f"Unexpected or missing workflow: {sorted(actual)}")
    wf = workflows / "build-android.yml"
    if wf.is_file():
        contents = wf.read_text("utf-8")
        for marker in ("python3 scripts/sovereignty_gate.py",
                       "python3 -m unittest discover -s tests -v",
                       "needs: sovereignty-gate"):
            if marker not in contents:
                errors.append(f"Mandatory job dependency missing: {marker}")
    return errors

if __name__ == "__main__":
    root = pathlib.Path(__file__).resolve().parent.parent
    if len(sys.argv) == 3 and sys.argv[1] == "--root":
        root = pathlib.Path(sys.argv[2]).resolve()
    issues = check(root)
    if issues:
        print("SOVEREIGNTY GATE: FAIL")
        for issue in issues:
            print(" -", issue)
        sys.exit(1)
    print("SOVEREIGNTY GATE: PASS (HUD-only, no map/tile/search/image released)")
