from pathlib import Path

import fitz
from PIL import Image

from tools.bp_v6.render_pdf import render_pdf


def _sample_pdf(path: Path) -> None:
    document = fitz.open()
    for index in range(2):
        page = document.new_page(width=960, height=540)
        page.insert_text((80, 100), f"Page {index + 1}", fontsize=36)
    document.save(path)
    document.close()


def test_renders_pages_and_contact_sheet(tmp_path):
    source = tmp_path / "sample.pdf"
    output = tmp_path / "render"
    _sample_pdf(source)

    report = render_pdf(source, output, dpi=96, columns=2)

    assert report["page_count"] == 2
    assert [Path(path).name for path in report["pages"]] == [
        "page-01.png",
        "page-02.png",
    ]
    contact_sheet = Path(report["contact_sheet"])
    assert contact_sheet.is_file()
    with Image.open(contact_sheet) as image:
        assert image.format == "PNG"
        assert image.width > 500
        assert image.height > 200
