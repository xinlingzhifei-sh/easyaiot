from pathlib import Path


def test_export_script_uses_isolated_headless_libreoffice_profile():
    script = Path("tools/bp_v6/export_pdf.ps1")
    assert script.is_file()
    text = script.read_text(encoding="utf-8")
    assert "soffice.com" in text
    assert "-env:UserInstallation=" in text
    assert '"--headless"' in text
    assert '"--convert-to"' in text
    assert '"pdf"' in text
    assert "Test-Path -LiteralPath $expected" in text
