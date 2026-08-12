"""Create the final competition workbook with a relative BP V6 material link."""

from __future__ import annotations

import argparse
import shutil
from pathlib import Path, PurePath

from openpyxl import load_workbook


DEFAULT_PDF_NAME = "逸飞AI智眼系统_创业大赛BP_V6.pdf"
DEFAULT_PPTX_NAME = "逸飞AI智眼系统_创业大赛BP_V6.pptx"


def _validate_pdf_name(pdf_name: str) -> None:
    if (
        not pdf_name
        or Path(pdf_name).is_absolute()
        or "/" in pdf_name
        or "\\" in pdf_name
        or PurePath(pdf_name).name != pdf_name
        or not pdf_name.lower().endswith(".pdf")
    ):
        raise ValueError("pdf_name must be a relative PDF filename")


def _validate_pptx_name(pptx_name: str) -> None:
    if (
        not pptx_name
        or Path(pptx_name).is_absolute()
        or "/" in pptx_name
        or "\\" in pptx_name
        or PurePath(pptx_name).name != pptx_name
        or not pptx_name.lower().endswith(".pptx")
    ):
        raise ValueError("pptx_name must be a relative PPTX filename")


def _update_link(source: str | Path, destination: str | Path, filename: str) -> Path:
    source_path = Path(source)
    destination_path = Path(destination)
    if not source_path.is_file():
        raise FileNotFoundError(source_path)
    destination_path.parent.mkdir(parents=True, exist_ok=True)
    if source_path.resolve() == destination_path.resolve():
        raise ValueError("destination must differ from source workbook")

    shutil.copy2(source_path, destination_path)
    workbook = load_workbook(destination_path, data_only=False, keep_links=True)
    try:
        cell = workbook.worksheets[0]["J3"]
        cell.value = filename
        cell.hyperlink = filename
        workbook.save(destination_path)
    finally:
        workbook.close()
    return destination_path


def update_pdf_link(
    source: str | Path,
    destination: str | Path,
    pdf_name: str = DEFAULT_PDF_NAME,
) -> Path:
    """Copy a workbook and update only first-sheet J3 value and hyperlink."""

    _validate_pdf_name(pdf_name)
    return _update_link(source, destination, pdf_name)


def update_pptx_link(
    source: str | Path,
    destination: str | Path,
    pptx_name: str = DEFAULT_PPTX_NAME,
) -> Path:
    """Copy a workbook and update only first-sheet J3 to a PPTX link."""

    _validate_pptx_name(pptx_name)
    return _update_link(source, destination, pptx_name)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("destination", type=Path)
    parser.add_argument("--pdf-name", default=DEFAULT_PDF_NAME)
    args = parser.parse_args()
    print(update_pdf_link(args.source, args.destination, args.pdf_name))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
