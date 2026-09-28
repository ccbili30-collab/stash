#!/usr/bin/env python3
"""Lucide stroke SVG → Android VectorDrawable，命名沿用 HugeIcons 体系。

视觉规范 v2：图标走 stroke 细线圆头风格（HugeIcons stroke 同族）。
Lucide 的 fill/stroke 属性挂在 <svg> 根上由子元素继承，VectorDrawable
没有继承，所以这里读根属性作默认、子元素可覆盖，再摊平图元为 path。
生成物由上层 tint 单色渲染。
"""
from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

SRC = Path(__file__).parent / "lucide"
DST = Path(__file__).parent.parent / "app/src/main/res/drawable"

# Lucide 文件名 → HugeIcons 名（Kotlin/资源命名沿用 HugeIcons 习惯）
MAPPING = {
    "search": "Search01",
    "settings": "Settings01",
    "tag": "Tag01",
    "link": "Link01",
    "image": "Image01",
    "camera": "Camera01",
    "scan": "Scan01",
    "trash-2": "Delete01",
    "pencil": "Edit01",
    "share-2": "Share01",
    "arrow-left": "ArrowLeft01",
    "x": "Cancel01",
    "download": "Download01",
    "refresh-cw": "Refresh01",
    "arrow-up-right": "ArrowUpRight01",
    "plus": "Add01",
    "check": "Tick01",
    "alert-circle": "Alert01",
    "archive": "Archive01",
    "globe": "Globe01",
    "accessibility": "Accessibility01",
    "info": "Information01",
    "bell": "Notification01",
    "arrow-down": "ArrowDown01",
    "arrow-up": "ArrowUp01",
}

ROOT_DEFAULTS = {"fill": "none", "stroke": "currentColor", "stroke-width": "2",
                 "stroke-linecap": "round", "stroke-linejoin": "round"}


def num(s: str) -> float:
    return float(s)


def circle_to_path(cx: float, cy: float, r: float) -> str:
    return f"M {cx - r},{cy} A {r},{r} 0 1 0 {cx + r},{cy} A {r},{r} 0 1 0 {cx - r},{cy} Z"


def rect_to_path(x: float, y: float, w: float, h: float, rx: float) -> str:
    rx = rx or 0
    ry = None
    d = min(rx, w / 2, h / 2) if rx else 0
    if d > 0:
        return (
            f"M {x + d},{y} H {x + w - d} A {d},{d} 0 0 1 {x + w},{y + d} "
            f"V {y + h - d} A {d},{d} 0 0 1 {x + w - d},{y + h} "
            f"H {x + d} A {d},{d} 0 0 1 {x},{y + h - d} "
            f"V {y + d} A {d},{d} 0 0 1 {x + d},{y} Z"
        )
    return f"M {x},{y} H {x + w} V {y + h} H {x} Z"


def points_to_path(points: str, close: bool) -> str:
    pts = [p for p in re.split(r"[\s,]+", points.strip()) if p]
    coords = [(num(pts[i]), num(pts[i + 1])) for i in range(0, len(pts) - 1, 2)]
    if not coords:
        return ""
    d = f"M {coords[0][0]},{coords[0][1]}"
    for x, y in coords[1:]:
        d += f" L {x},{y}"
    return d + (" Z" if close else "")


def svg_to_vector(svg_path: Path, huge_name: str) -> str:
    root = ET.parse(svg_path).getroot()
    vb = (root.get("viewBox") or "0 0 24 24").split()
    vw, vh = vb[2], vb[3]

    def root_attr(name: str) -> str | None:
        return root.get(name) or ROOT_DEFAULTS.get(name)

    paths = []
    for el in root.iter():
        tag = el.tag.split("}")[-1]
        if tag == "svg":
            continue

        def paint(name: str) -> str | None:
            return el.get(name) or root_attr(name)

        d = ""
        if tag == "path":
            d = el.get("d", "")
        elif tag == "circle":
            d = circle_to_path(num(el.get("cx", "0")), num(el.get("cy", "0")), num(el.get("r", "0")))
        elif tag == "line":
            d = (f"M {num(el.get('x1', '0'))},{num(el.get('y1', '0'))} "
                 f"L {num(el.get('x2', '0'))},{num(el.get('y2', '0'))}")
        elif tag == "rect":
            d = rect_to_path(
                num(el.get("x", "0")), num(el.get("y", "0")),
                num(el.get("width", "0")), num(el.get("height", "0")),
                num(el.get("rx", "0") or 0),
            )
        elif tag == "polyline":
            d = points_to_path(el.get("points", ""), close=False)
        elif tag == "polygon":
            d = points_to_path(el.get("points", ""), close=True)
        if not d:
            continue

        attrs = []
        fill = paint("fill")
        attrs.append('android:fillColor="#FF000000"' if fill != "none" else 'android:fillColor="#00000000"')
        if el.get("fill-rule") == "evenodd":
            attrs.append('android:fillType="evenOdd"')
        stroke = paint("stroke")
        if stroke and stroke != "none":
            attrs.append('android:strokeColor="#FF000000"')
            attrs.append(f'android:strokeWidth="{paint("stroke-width") or 2}"')
            cap = paint("stroke-linecap")
            if cap and cap != "inherit":
                attrs.append(f'android:strokeLineCap="{cap}"')
            join = paint("stroke-linejoin")
            if join and join != "inherit":
                attrs.append(f'android:strokeLineJoin="{join}"')
        paths.append(f'    <path\n        android:pathData="{d}"\n        {" ".join(attrs)}/>')

    return (
        f'<!-- {huge_name} (Lucide stroke, MIT) -->\n'
        f'<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        f'    android:width="24dp"\n    android:height="24dp"\n'
        f'    android:viewportWidth="{vw}"\n    android:viewportHeight="{vh}">\n'
        + "\n".join(paths)
        + "\n</vector>\n"
    )


def main() -> None:
    DST.mkdir(parents=True, exist_ok=True)
    count = 0
    for lucide, huge in sorted(MAPPING.items()):
        svg = SRC / f"{lucide}.svg"
        if not svg.exists() or svg.stat().st_size < 50:
            print(f"!! missing {svg}")
            continue
        res_name = "ic_hi_" + huge.lower()
        (DST / f"{res_name}.xml").write_text(svg_to_vector(svg, huge))
        print(f"{lucide}.svg -> {res_name}.xml ({huge})")
        count += 1
    print(f"\n{count}/{len(MAPPING)} icons generated")


if __name__ == "__main__":
    sys.exit(main())
