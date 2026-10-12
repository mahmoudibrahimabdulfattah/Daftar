#!/usr/bin/env python3
"""Generate Daftar's brand and platform icon assets from one notebook geometry.

The mark is the 48-unit notebook from docs/design/daftar.html and core:designsystem DaftarMark.
Every output here (canonical SVGs, Android vector drawables and colours, iOS app icons, launch
images and their catalog metadata) is derived from the constants below, so the platforms cannot drift.

Usage:
    python3 scripts/generate-brand-assets.py           # write all generated assets
    python3 scripts/generate-brand-assets.py --check   # regenerate into a temp dir and compare

Hand-written files (not generated): iosApp/iosApp/LaunchScreen.storyboard and the
iosApp/iosApp/{en,ar}.lproj/InfoPlist.strings localisations.

Rasterisation uses cairosvg (cairo) and Pillow; see scripts/requirements-brand.txt for the pinned versions.
Output is deterministic: no timestamps, no metadata other than an sRGB chunk, fixed compression.
"""

from __future__ import annotations

import argparse
import filecmp
import io
import json
import sys
import tempfile
from pathlib import Path

import cairosvg
from PIL import Image
from PIL.PngImagePlugin import PngInfo

ROOT = Path(__file__).resolve().parents[1]

# ---------------------------------------------------------------------------
# Locked palette
# ---------------------------------------------------------------------------

OLIVE = "#45583D"
PAPER_SURFACE = "#FFFEF8"
PAPER = "#F5F3EB"
NIGHT_INK = "#191E18"
NIGHT_MARK = "#BFD0AD"
BLACK = "#000000"
WHITE = "#FFFFFF"

# Android launcher: olive plate, paper notebook, cut-outs in the plate colour.
LAUNCHER_BACKGROUND = OLIVE
LAUNCHER_MARK = PAPER_SURFACE
LAUNCHER_DETAIL = OLIVE

# iOS light appearance matches the launcher plate.
IOS_LIGHT_BACKGROUND = OLIVE
IOS_LIGHT_MARK = PAPER_SURFACE
IOS_LIGHT_DETAIL = OLIVE

IOS_DARK_BACKGROUND = NIGHT_INK
IOS_DARK_MARK = NIGHT_MARK
IOS_DARK_DETAIL = NIGHT_INK

IOS_TINTED_BACKGROUND = BLACK
IOS_TINTED_MARK = WHITE
IOS_TINTED_DETAIL = BLACK

SPLASH_LIGHT_BACKGROUND = PAPER
SPLASH_LIGHT_MARK = OLIVE
SPLASH_LIGHT_DETAIL = PAPER

SPLASH_DARK_BACKGROUND = NIGHT_INK
SPLASH_DARK_MARK = NIGHT_MARK
SPLASH_DARK_DETAIL = NIGHT_INK

# ---------------------------------------------------------------------------
# Canonical geometry (48-unit viewBox)
# ---------------------------------------------------------------------------

COVER_X, COVER_Y, COVER_W, COVER_H, COVER_R = 10.0, 6.0, 28.0, 36.0, 5.0
SPINE_X, SPINE_Y1, SPINE_Y2, SPINE_W = 18.0, 7.0, 41.0, 2.0
RULE_X1, RULE_X2 = 24.0, 32.0
RULE_YS = (17.0, 24.0)
RULE_W = 2.5

# Platform transforms: (scale, translate x, translate y) applied to the canonical mark.
ANDROID_FOREGROUND = (1.25, 24.0, 24.0)  # 108 viewport, notebook 35x45 at the centre
ANDROID_SPLASH = (2.0, 96.0, 96.0)  # 288 viewport, notebook 56x72 at the centre
IOS_ICON = (40.0 / 3.0, 192.0, 192.0)  # 1024 viewport, mark box 640x640 at the centre
LAUNCH_POINT = (2.0, 0.0, 0.0)  # 96 pt viewport, notebook 56x72 at the centre


def fmt(value: float) -> str:
    """Stable number formatting for SVG and VectorDrawable attributes."""
    rounded = round(value, 4)
    if rounded == int(rounded):
        return str(int(rounded))
    return f"{rounded:.4f}".rstrip("0").rstrip(".")


# ---------------------------------------------------------------------------
# SVG output
# ---------------------------------------------------------------------------


def mark_svg(frame: str, detail: str, transform: str | None = None) -> str:
    """The canonical mark, with the cover filled and the spine and rules stroked in the detail colour."""
    body = (
        f'<rect x="{fmt(COVER_X)}" y="{fmt(COVER_Y)}" width="{fmt(COVER_W)}" height="{fmt(COVER_H)}" '
        f'rx="{fmt(COVER_R)}" fill="{frame}"/>'
        f'<path d="M{fmt(SPINE_X)} {fmt(SPINE_Y1)}V{fmt(SPINE_Y2)}" fill="none" stroke="{detail}" '
        f'stroke-width="{fmt(SPINE_W)}"/>'
        + "".join(
            f'<path d="M{fmt(RULE_X1)} {fmt(y)}H{fmt(RULE_X2)}" fill="none" stroke="{detail}" '
            f'stroke-width="{fmt(RULE_W)}" stroke-linecap="round"/>'
            for y in RULE_YS
        )
    )
    if transform is None:
        return body
    return f'<g transform="{transform}">{body}</g>'


def transform_attr(scale: float, tx: float, ty: float) -> str:
    return f"translate({fmt(tx)} {fmt(ty)}) scale({fmt(scale)})"


def svg_document(width: float, height: float, content: str) -> str:
    return (
        '<svg xmlns="http://www.w3.org/2000/svg" '
        f'width="{fmt(width)}" height="{fmt(height)}" viewBox="0 0 {fmt(width)} {fmt(height)}">'
        f"{content}</svg>\n"
    )


def canonical_mark_svg() -> str:
    return svg_document(48, 48, mark_svg(IOS_LIGHT_MARK, IOS_LIGHT_DETAIL))


def icon_svg(background: str, frame: str, detail: str) -> str:
    scale, tx, ty = IOS_ICON
    content = f'<rect width="1024" height="1024" fill="{background}"/>' + mark_svg(
        frame, detail, transform_attr(scale, tx, ty)
    )
    return svg_document(1024, 1024, content)


def launch_svg(frame: str, detail: str) -> str:
    scale, tx, ty = LAUNCH_POINT
    # The launch image is a 96 pt square with transparent padding; no background plate.
    return svg_document(96, 96, mark_svg(frame, detail, transform_attr(scale, tx, ty)))


# ---------------------------------------------------------------------------
# Android VectorDrawable output (filled geometry, transforms baked into coordinates)
# ---------------------------------------------------------------------------


class _Map:
    def __init__(self, scale: float, tx: float, ty: float) -> None:
        self.s, self.tx, self.ty = scale, tx, ty

    def pt(self, x: float, y: float) -> tuple[float, float]:
        return self.tx + self.s * x, self.ty + self.s * y

    def xy(self, x: float, y: float) -> str:
        px, py = self.pt(x, y)
        return f"{fmt(px)},{fmt(py)}"

    def r(self, radius: float) -> str:
        return fmt(self.s * radius)


def _rounded_rect(m: _Map, x0: float, y0: float, x1: float, y1: float, r: float) -> str:
    rr = m.r(r)
    p = m.pt
    return (
        f"M{m.xy(x0 + r, y0)}"
        f"H{fmt(p(x1 - r, y0)[0])}"
        f"A{rr},{rr} 0 0 1 {m.xy(x1, y0 + r)}"
        f"V{fmt(p(x1, y1 - r)[1])}"
        f"A{rr},{rr} 0 0 1 {m.xy(x1 - r, y1)}"
        f"H{fmt(p(x0 + r, y1)[0])}"
        f"A{rr},{rr} 0 0 1 {m.xy(x0, y1 - r)}"
        f"V{fmt(p(x0, y0 + r)[1])}"
        f"A{rr},{rr} 0 0 1 {m.xy(x0 + r, y0)}"
        "Z"
    )


def _spine_path(m: _Map) -> str:
    half = SPINE_W / 2
    x0, x1 = SPINE_X - half, SPINE_X + half
    return f"M{m.xy(x0, SPINE_Y1)}H{fmt(m.pt(x1, 0)[0])}V{fmt(m.pt(0, SPINE_Y2)[1])}H{fmt(m.pt(x0, 0)[0])}Z"


def _rule_path(m: _Map, y: float) -> str:
    r = RULE_W / 2
    return (
        f"M{m.xy(RULE_X1, y - r)}"
        f"H{fmt(m.pt(RULE_X2, 0)[0])}"
        f"A{m.r(r)},{m.r(r)} 0 0 1 {m.xy(RULE_X2, y + r)}"
        f"H{fmt(m.pt(RULE_X1, 0)[0])}"
        f"A{m.r(r)},{m.r(r)} 0 0 1 {m.xy(RULE_X1, y - r)}"
        "Z"
    )


def _cover(m: _Map) -> str:
    return _rounded_rect(m, COVER_X, COVER_Y, COVER_X + COVER_W, COVER_Y + COVER_H, COVER_R)


def _vector(size: int, body: str) -> str:
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        f'    android:width="{size}dp"\n'
        f'    android:height="{size}dp"\n'
        f'    android:viewportWidth="{size}"\n'
        f'    android:viewportHeight="{size}">\n'
        f"{body}"
        "</vector>\n"
    )


def _filled_layer(fill: str, data: str, fill_type: str | None = None) -> str:
    extra = f'\n        android:fillType="{fill_type}"' if fill_type else ""
    return f'    <path\n        android:fillColor="{fill}"{extra}\n        android:pathData="{data}" />\n'


def android_foreground_xml() -> str:
    """Adaptive foreground: notebook on a transparent layer; the spine and rules are plate-coloured cut-outs."""
    m = _Map(*ANDROID_FOREGROUND)
    details = " ".join([_spine_path(m), *[_rule_path(m, y) for y in RULE_YS]])
    body = _filled_layer(LAUNCHER_MARK, _cover(m)) + _filled_layer(LAUNCHER_DETAIL, details)
    return _vector(108, body)


def android_monochrome_xml() -> str:
    """Monochrome mask: one opaque silhouette whose spine and rules are real holes (even-odd)."""
    m = _Map(*ANDROID_FOREGROUND)
    data = " ".join([_cover(m), _spine_path(m), *[_rule_path(m, y) for y in RULE_YS]])
    return _vector(108, _filled_layer(BLACK, data, fill_type="evenOdd"))


def android_splash_xml(frame: str, detail: str) -> str:
    m = _Map(*ANDROID_SPLASH)
    details = " ".join([_spine_path(m), *[_rule_path(m, y) for y in RULE_YS]])
    body = _filled_layer(frame, _cover(m)) + _filled_layer(detail, details)
    return _vector(288, body)


def android_adaptive_icon_xml() -> str:
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
        '    <background android:drawable="@color/ic_launcher_background" />\n'
        '    <foreground android:drawable="@drawable/ic_launcher_foreground" />\n'
        '    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />\n'
        "</adaptive-icon>\n"
    )


def android_colors_xml(entries: dict[str, str]) -> str:
    lines = "".join(f'    <color name="{name}">{value}</color>\n' for name, value in entries.items())
    return f'<?xml version="1.0" encoding="utf-8"?>\n<resources>\n{lines}</resources>\n'


# ---------------------------------------------------------------------------
# Raster output
# ---------------------------------------------------------------------------


def _png(svg: str, size: int, *, opaque: bool) -> bytes:
    raw = cairosvg.svg2png(bytestring=svg.encode("utf-8"), output_width=size, output_height=size)
    image = Image.open(io.BytesIO(raw))
    image = image.convert("RGB" if opaque else "RGBA")
    info = PngInfo()
    info.add(b"sRGB", b"\x00")  # perceptual rendering intent; the pixels are sRGB
    out = io.BytesIO()
    image.save(out, format="PNG", pnginfo=info, optimize=False, compress_level=9)
    return out.getvalue()


# ---------------------------------------------------------------------------
# Catalog metadata
# ---------------------------------------------------------------------------


def _xcode_json(obj: dict) -> bytes:
    text = json.dumps(obj, indent=2, sort_keys=True).replace('": ', '" : ') + "\n"
    return text.encode("utf-8")


def _info() -> dict:
    return {"author": "xcode", "version": 1}


def _hex_components(hex_value: str) -> dict[str, str]:
    value = hex_value.lstrip("#")
    red, green, blue = (int(value[i : i + 2], 16) for i in (0, 2, 4))
    return {"alpha": "1.000", "blue": f"0x{blue:02X}", "green": f"0x{green:02X}", "red": f"0x{red:02X}"}


def _colour_entry(hex_value: str, dark: bool) -> dict:
    entry: dict = {
        "color": {"color-space": "srgb", "components": _hex_components(hex_value)},
        "idiom": "universal",
    }
    if dark:
        entry["appearances"] = [{"appearance": "luminosity", "value": "dark"}]
    return entry


def app_icon_contents() -> bytes:
    def image(filename: str, appearance: str | None) -> dict:
        entry = {"filename": filename, "idiom": "universal", "platform": "ios", "size": "1024x1024"}
        if appearance:
            entry["appearances"] = [{"appearance": "luminosity", "value": appearance}]
        return entry

    return _xcode_json(
        {
            "images": [
                image("icon-light.png", None),
                image("icon-dark.png", "dark"),
                image("icon-tinted.png", "tinted"),
            ],
            "info": _info(),
        }
    )


def launch_background_contents() -> bytes:
    return _xcode_json(
        {
            "colors": [
                _colour_entry(SPLASH_LIGHT_BACKGROUND, dark=False),
                _colour_entry(SPLASH_DARK_BACKGROUND, dark=True),
            ],
            "info": _info(),
        }
    )


def launch_mark_contents() -> bytes:
    images = []
    for mode in ("light", "dark"):
        for scale in ("1x", "2x", "3x"):
            entry = {"filename": f"launch-{mode}@{scale}.png", "idiom": "universal", "scale": scale}
            if mode == "dark":
                entry["appearances"] = [{"appearance": "luminosity", "value": "dark"}]
            images.append(entry)
    return _xcode_json({"images": images, "info": _info()})


# ---------------------------------------------------------------------------
# Output set
# ---------------------------------------------------------------------------

BRAND = "docs/design/brand"
ANDROID_RES = "androidApp/src/main/res"
IOS_ASSETS = "iosApp/iosApp/Assets.xcassets"


def generate() -> dict[str, bytes]:
    out: dict[str, bytes] = {}

    def text(rel: str, content: str) -> None:
        out[rel] = content.encode("utf-8")

    # Canonical SVG sources.
    text(f"{BRAND}/daftar-mark.svg", canonical_mark_svg())
    text(f"{BRAND}/daftar-icon-light.svg", icon_svg(IOS_LIGHT_BACKGROUND, IOS_LIGHT_MARK, IOS_LIGHT_DETAIL))
    text(f"{BRAND}/daftar-icon-dark.svg", icon_svg(IOS_DARK_BACKGROUND, IOS_DARK_MARK, IOS_DARK_DETAIL))
    text(f"{BRAND}/daftar-icon-tinted.svg", icon_svg(IOS_TINTED_BACKGROUND, IOS_TINTED_MARK, IOS_TINTED_DETAIL))
    text(f"{BRAND}/daftar-launch-light.svg", launch_svg(SPLASH_LIGHT_MARK, SPLASH_LIGHT_DETAIL))
    text(f"{BRAND}/daftar-launch-dark.svg", launch_svg(SPLASH_DARK_MARK, SPLASH_DARK_DETAIL))

    # Android.
    text(f"{ANDROID_RES}/drawable/ic_launcher_foreground.xml", android_foreground_xml())
    text(f"{ANDROID_RES}/drawable/ic_launcher_monochrome.xml", android_monochrome_xml())
    text(f"{ANDROID_RES}/drawable/splash_mark.xml", android_splash_xml(SPLASH_LIGHT_MARK, SPLASH_LIGHT_DETAIL))
    text(f"{ANDROID_RES}/drawable-night/splash_mark.xml", android_splash_xml(SPLASH_DARK_MARK, SPLASH_DARK_DETAIL))
    text(
        f"{ANDROID_RES}/values/colors.xml",
        android_colors_xml({"ic_launcher_background": LAUNCHER_BACKGROUND, "splash_background": SPLASH_LIGHT_BACKGROUND}),
    )
    text(
        f"{ANDROID_RES}/values-night/colors.xml",
        android_colors_xml({"splash_background": SPLASH_DARK_BACKGROUND}),
    )
    text(f"{ANDROID_RES}/mipmap-anydpi/ic_launcher.xml", android_adaptive_icon_xml())
    text(f"{ANDROID_RES}/mipmap-anydpi/ic_launcher_round.xml", android_adaptive_icon_xml())

    # iOS app icons: 1024 square, opaque RGB.
    icon = f"{IOS_ASSETS}/AppIcon.appiconset"
    out[f"{icon}/icon-light.png"] = _png(icon_svg(IOS_LIGHT_BACKGROUND, IOS_LIGHT_MARK, IOS_LIGHT_DETAIL), 1024, opaque=True)
    out[f"{icon}/icon-dark.png"] = _png(icon_svg(IOS_DARK_BACKGROUND, IOS_DARK_MARK, IOS_DARK_DETAIL), 1024, opaque=True)
    out[f"{icon}/icon-tinted.png"] = _png(
        icon_svg(IOS_TINTED_BACKGROUND, IOS_TINTED_MARK, IOS_TINTED_DETAIL), 1024, opaque=True
    )
    out[f"{icon}/Contents.json"] = app_icon_contents()

    # iOS launch: colour set and mark images at 1x, 2x and 3x of a 96 pt box.
    out[f"{IOS_ASSETS}/LaunchBackground.colorset/Contents.json"] = launch_background_contents()
    mark = f"{IOS_ASSETS}/LaunchMark.imageset"
    for scale in (1, 2, 3):
        px = 96 * scale
        out[f"{mark}/launch-light@{scale}x.png"] = _png(launch_svg(SPLASH_LIGHT_MARK, SPLASH_LIGHT_DETAIL), px, opaque=False)
        out[f"{mark}/launch-dark@{scale}x.png"] = _png(launch_svg(SPLASH_DARK_MARK, SPLASH_DARK_DETAIL), px, opaque=False)
    out[f"{mark}/Contents.json"] = launch_mark_contents()

    return dict(sorted(out.items()))


def write(root: Path, outputs: dict[str, bytes]) -> None:
    for rel, data in outputs.items():
        path = root / rel
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data)


def check() -> int:
    outputs = generate()
    with tempfile.TemporaryDirectory(prefix="daftar-brand-") as tmp:
        scratch = Path(tmp)
        write(scratch, outputs)
        problems = []
        for rel in outputs:
            committed = ROOT / rel
            if not committed.is_file():
                problems.append(f"missing: {rel}")
            elif not filecmp.cmp(scratch / rel, committed, shallow=False):
                problems.append(f"stale: {rel}")
    if problems:
        print("Brand assets are out of date. Run: python3 scripts/generate-brand-assets.py", file=sys.stderr)
        for problem in problems:
            print(f"  {problem}", file=sys.stderr)
        return 1
    print(f"Brand assets up to date ({len(outputs)} files).")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--check", action="store_true", help="regenerate into a temp dir and compare with the repo")
    args = parser.parse_args()
    if args.check:
        return check()
    outputs = generate()
    write(ROOT, outputs)
    print(f"Wrote {len(outputs)} brand assets.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
