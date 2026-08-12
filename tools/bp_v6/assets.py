"""Validation helpers for the three anonymized BP V6 product screenshots."""

from __future__ import annotations

import hashlib
from pathlib import Path

from PIL import Image


EXPECTED_ASSETS = (
    "algorithm-task.png",
    "device-archive.png",
    "clue-review.png",
)

MIN_WIDTH = 1400
MIN_HEIGHT = 760


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest().upper()


def validate_assets(directory: str | Path) -> dict[str, dict[str, int | str]]:
    """Validate required filenames, PNG format, dimensions, and uniqueness."""

    root = Path(directory)
    missing = [name for name in EXPECTED_ASSETS if not (root / name).is_file()]
    if missing:
        raise ValueError(f"missing screenshot assets: {', '.join(missing)}")

    report: dict[str, dict[str, int | str]] = {}
    for name in EXPECTED_ASSETS:
        path = root / name
        with Image.open(path) as image:
            width, height = image.size
            image_format = image.format
        if image_format != "PNG":
            raise ValueError(f"asset must be PNG: {name}")
        if width < MIN_WIDTH or height < MIN_HEIGHT:
            raise ValueError(
                f"asset too small: {name} is {width}x{height}; "
                f"minimum is {MIN_WIDTH}x{MIN_HEIGHT}"
            )
        report[name] = {
            "format": image_format,
            "width": width,
            "height": height,
            "sha256": _sha256(path),
        }

    hashes = [item["sha256"] for item in report.values()]
    if len(set(hashes)) != len(hashes):
        raise ValueError("screenshot assets must be distinct")
    return report
