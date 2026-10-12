#!/usr/bin/env python3
"""Regenerate the ic_* VectorDrawables from Phosphor (MIT).

usage: gen_phosphor_icons.py <unpacked @phosphor-icons/core package dir>
Object icons use the Fill weight, action glyphs use Bold (Linear-style).
"""
import json
import re
import sys
from pathlib import Path

ICONS = {
    "ic_calendar_01": "fill/calendar-dot",
    "ic_calendar_02": "fill/calendar-dots",
    "ic_inbox": "fill/tray",
    "ic_clock_01": "fill/clock",
    "ic_book_open": "fill/book-open",
    "ic_delete": "fill/trash",
    "ic_folder": "fill/folder-simple",
    "ic_chart": "fill/chart-bar",
    "ic_settings": "fill/gear-six",
    "ic_grid": "fill/squares-four",
    "ic_help_circle": "fill/question",
    "ic_compose": "fill/note-pencil",
    "ic_repeat": "fill/arrows-clockwise",
    "ic_arrow_left": "bold/arrow-left",
    "ic_arrow_up": "bold/arrow-up",
    "ic_check": "bold/check",
    "ic_plus": "bold/plus",
    "ic_maximize": "bold/arrows-out-simple",
    "ic_more_h": "bold/dots-three",
}

pkg = Path(sys.argv[1])
version = json.loads((pkg / "package.json").read_text())["version"]
res = Path(__file__).resolve().parent.parent / "app/src/main/res/drawable"
for name, src in ICONS.items():
    weight, icon = src.split("/")
    svg = (pkg / "assets" / weight / f"{icon}-{weight}.svg").read_text()
    body = re.sub(r"^<svg[^>]*>|</svg>\s*$", "", svg.strip())
    paths = re.findall(r'<path d="([^"]+)"/>', body)
    assert "".join(f'<path d="{d}"/>' for d in paths) == body, f"{src}: unsupported SVG element"
    xml = [
        f"<!-- Phosphor {icon} ({weight}) @phosphor-icons/core {version}, MIT. tools/gen_phosphor_icons.py -->",
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        '    android:width="24dp" android:height="24dp"',
        '    android:viewportWidth="256" android:viewportHeight="256">',
        *(f'    <path android:pathData="{d}" android:fillColor="#FF000000"/>' for d in paths),
        "</vector>",
        "",
    ]
    (res / f"{name}.xml").write_text("\n".join(xml))
print(f"{len(ICONS)} icons from Phosphor {version}")
