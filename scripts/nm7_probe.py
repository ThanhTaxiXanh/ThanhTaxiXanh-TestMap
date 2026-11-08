#!/usr/bin/env python3
"""Read-only Navitel NM7 container triage. Does not decode or export map data.

Purpose:
- Compare first bytes against published TrID signatures for Navitel NM7.
- Identify candidate indexing markers (NIDXR), sampled entropy and file hashes.
- Produce a local JSON report without copying/uploading map bytes.
- No keys, license bypass, DRM workarounds, geodata extraction or network calls.

Known signature source:
https://github.com/digipres/digipres.github.io/blob/master/_sources/registries/trid/triddefs_xml/defs/n/nm7-navitel.trid.xml

Only a signature match is verified. NIDXR's actual data structure is unknown.
"""
from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import json
import math
from pathlib import Path

TRID_PREFIX = bytes.fromhex("474E4D3304000000FFFFFFFF")
INDEX_MARKER = b"NIDXR"
CHUNK_SIZE = 1024 * 1024
SAMPLE_SIZE = 64 * 1024
SCAN_LIMIT = 1024 * 1024


def shannon_entropy(data: bytes) -> float:
    if not data:
        return 0.0
    frequency = Counter(data)
    length = len(data)
    return -sum((n / length) * math.log2(n / length)
                for n in frequency.values())


def hash_file(path: Path) -> str:
    sha = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(CHUNK_SIZE), b""):
            sha.update(chunk)
    return sha.hexdigest()


def find_all(haystack: bytes, needle: bytes, limit: int = 20) -> list[int]:
    hits = []
    pos = 0
    while len(hits) < limit:
        at = haystack.find(needle, pos)
        if at == -1:
            break
        hits.append(at)
        pos = at + 1
    return hits


def inspect_nm7(path: Path) -> dict:
    """Bounded-memory analysis. Does not parse or decrypt proprietary content."""
    if not path.is_file():
        raise FileNotFoundError(str(path))
    size = path.stat().st_size
    if size == 0:
        raise ValueError("Empty file")

    with path.open("rb") as f:
        header = f.read(256)
        first = header[:min(64, len(header))]
        marker_at_24 = len(header) >= 29 and header[24:29] == INDEX_MARKER
        # Use non-overlapping logical labels, even if a small input causes
        # multiple samples to come from the same part of the file.
        offsets = sorted(set([
            0,
            max(0, size // 4 - SAMPLE_SIZE // 2),
            max(0, size // 2 - SAMPLE_SIZE // 2),
            max(0, (size * 3) // 4 - SAMPLE_SIZE // 2),
            max(0, size - SAMPLE_SIZE),
        ]))
        samples = []
        for offset in offsets:
            f.seek(offset)
            data = f.read(SAMPLE_SIZE)
            samples.append({
                "offset": offset,
                "bytes": len(data),
                "entropy_bits_per_byte": round(shannon_entropy(data), 4),
            })

        f.seek(0)
        first_megabyte = f.read(SCAN_LIMIT)

    markers = find_all(first_megabyte, INDEX_MARKER, limit=30)
    # These are only magic-like patterns, NOT validated compressed blocks.
    compression_candidates = {
        "gzip_1f8b08": find_all(first_megabyte, b"\x1f\x8b\x08", limit=8),
        "zlib_789c": find_all(first_megabyte, b"\x78\x9c", limit=8),
        "zlib_78da": find_all(first_megabyte, b"\x78\xda", limit=8),
        "zip_pk0304": find_all(first_megabyte, b"PK\x03\x04", limit=8),
    }
    return {
        "tool": "TTX NM7 header probe v0.1",
        "filename": path.name,
        "size_bytes": size,
        "sha256": hash_file(path),
        "first_64_bytes_hex": first.hex(" "),
        "signature": {
            "GNM3_at_0": header.startswith(b"GNM3"),
            "TrID_12_bytes_at_0": header.startswith(TRID_PREFIX),
            "NIDXR_at_24": marker_at_24,
            "likely_trid_family": header.startswith(TRID_PREFIX)
            and marker_at_24,
            "note": "Signature match is not validation of map, index, or road data.",
        },
        "marker_offsets_first_1MiB": {"NIDXR": markers},
        "signature_like_patterns_first_1MiB": compression_candidates,
        "entropy_samples": samples,
        "limits": {
            "index_structure_decoded": False,
            "speed_limit_records_decoded": 0,
            "traffic_sign_records_decoded": 0,
            "geodata_exported": False,
            "license_or_security_checks_bypassed": False,
        },
    }


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Read-only forensic header probe for Navitel .nm7 files."
    )
    parser.add_argument("file", type=Path, help="Path to VNM.nm7")
    parser.add_argument("--out", type=Path,
                        help="Optional local JSON report path")
    args = parser.parse_args()
    try:
        report = inspect_nm7(args.file.expanduser())
        result = json.dumps(report, ensure_ascii=False, indent=2)
        if args.out:
            output = args.out.expanduser()
            output.parent.mkdir(parents=True, exist_ok=True)
            output.write_text(result + "\n", encoding="utf-8")
            print(f"Saved: {output}")
        else:
            print(result)
        return 0
    except (ValueError, OSError) as error:
        parser.exit(2, f"NM7 probe error: {error}\n")


if __name__ == "__main__":
    raise SystemExit(main())
