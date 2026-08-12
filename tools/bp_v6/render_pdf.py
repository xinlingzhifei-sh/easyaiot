"""Render a PDF to per-page PNG files and a compact contact sheet."""

from __future__ import annotations

import argparse
import json
import math
from pathlib import Path

import fitz
from PIL import Image, ImageDraw, ImageFont


def _font(size: int):
    path = Path("C:/Windows/Fonts/msyh.ttc")
    return ImageFont.truetype(str(path), size) if path.is_file() else ImageFont.load_default()


def render_pdf(
    source: str | Path,
    output_dir: str | Path,
    *,
    dpi: int = 144,
    columns: int = 4,
) -> dict[str, object]:
    """Render every page and return a JSON-serializable artifact report."""

    pdf_path = Path(source)
    destination = Path(output_dir)
    destination.mkdir(parents=True, exist_ok=True)
    for stale in destination.glob("page-*.png"):
        stale.unlink()

    document = fitz.open(pdf_path)
    page_paths: list[Path] = []
    try:
        matrix = fitz.Matrix(dpi / 72, dpi / 72)
        for index, page in enumerate(document):
            pixmap = page.get_pixmap(matrix=matrix, alpha=False)
            page_path = destination / f"page-{index + 1:02d}.png"
            pixmap.save(page_path)
            page_paths.append(page_path)
    finally:
        document.close()

    if not page_paths:
        raise ValueError("PDF contains no pages")

    columns = max(1, min(columns, len(page_paths)))
    thumb_width = 560
    label_height = 34
    gap = 24
    padding = 28
    thumbnails: list[Image.Image] = []
    try:
        for path in page_paths:
            image = Image.open(path).convert("RGB")
            height = round(image.height * thumb_width / image.width)
            thumbnails.append(image.resize((thumb_width, height), Image.Resampling.LANCZOS))
        thumb_height = max(image.height for image in thumbnails)
        rows = math.ceil(len(thumbnails) / columns)
        sheet_width = padding * 2 + columns * thumb_width + (columns - 1) * gap
        sheet_height = padding * 2 + rows * (thumb_height + label_height) + (rows - 1) * gap
        sheet = Image.new("RGB", (sheet_width, sheet_height), (235, 239, 244))
        draw = ImageDraw.Draw(sheet)
        font = _font(18)
        for index, image in enumerate(thumbnails):
            row, col = divmod(index, columns)
            x = padding + col * (thumb_width + gap)
            y = padding + row * (thumb_height + label_height + gap)
            draw.rounded_rectangle(
                (x - 2, y - 2, x + thumb_width + 2, y + thumb_height + 2),
                radius=5,
                fill=(255, 255, 255),
                outline=(198, 208, 220),
                width=2,
            )
            sheet.paste(image, (x, y))
            draw.text((x, y + thumb_height + 8), f"PAGE {index + 1:02d}", font=font, fill=(31, 41, 55))
        contact_sheet = destination / "contact-sheet.png"
        sheet.save(contact_sheet, format="PNG", optimize=True)
    finally:
        for image in thumbnails:
            image.close()

    return {
        "source": str(pdf_path.resolve()),
        "page_count": len(page_paths),
        "pages": [str(path.resolve()) for path in page_paths],
        "contact_sheet": str(contact_sheet.resolve()),
        "dpi": dpi,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("output_dir", type=Path)
    parser.add_argument("--dpi", type=int, default=144)
    parser.add_argument("--columns", type=int, default=4)
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()
    report = render_pdf(args.source, args.output_dir, dpi=args.dpi, columns=args.columns)
    payload = json.dumps(report, ensure_ascii=False, indent=2)
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(payload, encoding="utf-8")
    print(payload)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
