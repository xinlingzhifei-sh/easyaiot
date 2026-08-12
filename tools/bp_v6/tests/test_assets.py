from pathlib import Path

import pytest
from PIL import Image

from tools.bp_v6.assets import EXPECTED_ASSETS, validate_assets


def _write_png(path: Path, color: tuple[int, int, int]) -> None:
    Image.new("RGB", (1600, 900), color).save(path, format="PNG")


def test_expected_asset_contract(tmp_path):
    assert EXPECTED_ASSETS == (
        "algorithm-task.png",
        "device-archive.png",
        "clue-review.png",
    )
    for index, name in enumerate(EXPECTED_ASSETS):
        _write_png(tmp_path / name, (20 + index, 40, 60))

    report = validate_assets(tmp_path)

    assert tuple(report) == EXPECTED_ASSETS
    assert all(item["format"] == "PNG" for item in report.values())
    assert all(item["width"] >= 1400 for item in report.values())
    assert all(item["height"] >= 760 for item in report.values())
    assert len({item["sha256"] for item in report.values()}) == 3


def test_rejects_missing_or_small_assets(tmp_path):
    _write_png(tmp_path / EXPECTED_ASSETS[0], (20, 40, 60))
    Image.new("RGB", (800, 600), (21, 40, 60)).save(
        tmp_path / EXPECTED_ASSETS[1], format="PNG"
    )

    with pytest.raises(ValueError, match="missing|too small"):
        validate_assets(tmp_path)
