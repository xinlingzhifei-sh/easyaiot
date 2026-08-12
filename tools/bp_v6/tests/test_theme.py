from pptx.util import Inches

from tools.bp_v6.theme import (
    FONT_BOLD,
    FONT_REGULAR,
    SLIDE_HEIGHT,
    SLIDE_WIDTH,
    add_footer,
    add_title,
    new_presentation,
)


def test_slide_size_and_fonts():
    prs = new_presentation()
    assert prs.slide_width == SLIDE_WIDTH
    assert prs.slide_height == SLIDE_HEIGHT
    assert FONT_REGULAR == "Microsoft YaHei"
    assert FONT_BOLD == "Microsoft YaHei"


def test_title_and_footer_are_editable_text():
    prs = new_presentation()
    slide = prs.slides.add_slide(prs.slide_layouts[6])
    add_title(slide, "测试标题", 3)
    add_footer(slide, 3)
    text = "\n".join(shape.text for shape in slide.shapes if hasattr(shape, "text"))
    assert "测试标题" in text
    assert "03" in text
    assert "逸飞AI智眼系统｜创业大赛 BP V6" in text

    page_marker = next(shape for shape in slide.shapes if getattr(shape, "text", "") == "03")
    footer_page = next(shape for shape in slide.shapes if getattr(shape, "text", "") == "03/16")
    assert page_marker.width >= Inches(0.65)
    assert footer_page.width >= Inches(1.75)
