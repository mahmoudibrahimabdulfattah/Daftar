#!/usr/bin/env python3
"""Rasterizes the official bank and broker marks into the density PNGs that the private build packages.

Reads each manifest's source directory (original bytes, verified against the manifest sha256), renders every mark with
headless Chrome into a transparent 38dp tile with the mark aspect-fitted in a 30dp box centred inside it (4dp
clearance), and writes one PNG per density under core/designsystem/src/officialBankArtwork/composeResources:
bank_* files from docs/design/bank-marks and broker_* files from docs/design/broker-marks.
Marks are never recoloured, inverted, stretched or cropped: the browser only scales the whole mark uniformly, and a
dark broker backing is painted by the app behind the transparent mark, never baked into the PNG.
Output hashes are recorded per file under `outputs` in each manifest; the original fields are left untouched.

Run from the repository root:  python3 tools/bank-marks/render.py
"""
import hashlib
import json
import os
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
# (manifest, source directory, asset key prefix). Each directory is verified against its own manifest.
COLLECTIONS = [
    (ROOT / "docs/design/bank-marks/manifest.json", ROOT / "docs/design/bank-marks/source", "bank_"),
    (ROOT / "docs/design/broker-marks/manifest.json", ROOT / "docs/design/broker-marks/source", "broker_"),
]
OUT = ROOT / "core/designsystem/src/officialBankArtwork/composeResources"
CHROME = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
TILE_DP = 38
MARK_DP = 30
DENSITIES = [("mdpi", 1), ("hdpi", 1.5), ("xhdpi", 2), ("xxhdpi", 3), ("xxxhdpi", 4)]


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def asset_key(prefix: str, institution_id: str) -> str:
    return prefix + institution_id.replace(".", "_").replace("-", "_")


def html_for(src: Path) -> str:
    inset = (TILE_DP - MARK_DP) / 2
    return f"""<!doctype html><html><head><meta charset="utf-8"><style>
html,body{{margin:0;padding:0;background:transparent;overflow:hidden}}
.tile{{position:relative;width:{TILE_DP}px;height:{TILE_DP}px;background:transparent}}
.mark{{position:absolute;left:{inset}px;top:{inset}px;width:{MARK_DP}px;height:{MARK_DP}px;
object-fit:contain;display:block}}
</style></head><body><div class="tile"><img class="mark" src="{src.as_uri()}"></div></body></html>"""


def render(src: Path, scale: float, out_png: Path, work: Path) -> None:
    page = work / (out_png.stem + f"_{scale}.html")
    page.write_text(html_for(src), encoding="utf-8")
    size = round(TILE_DP * scale)
    cmd = [
        CHROME, "--headless=new", "--disable-gpu", "--hide-scrollbars", "--no-first-run",
        # A private profile: headless Chrome must not attach to the user's running browser.
        f"--user-data-dir={work / 'profile'}",
        "--default-background-color=00000000",
        f"--force-device-scale-factor={scale}",
        f"--window-size={TILE_DP},{TILE_DP}",
        f"--screenshot={out_png}", page.as_uri(),
    ]
    subprocess.run(cmd, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=60)
    if not out_png.exists():
        raise SystemExit(f"render failed for {src.name} at {scale}x")
    png_size = png_dimensions(out_png)
    if png_size != (size, size):
        raise SystemExit(f"{src.name} at {scale}x rendered {png_size}, expected {(size, size)}")


def png_dimensions(path: Path):
    data = path.read_bytes()
    return int.from_bytes(data[16:20], "big"), int.from_bytes(data[20:24], "big")


def main() -> int:
    work = Path(tempfile.mkdtemp(prefix="bank-marks-"))
    total = 0
    for manifest_path, source_dir, prefix in COLLECTIONS:
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        for mark in manifest["marks"]:
            src = source_dir / mark["file"]
            actual = sha256(src)
            if actual != mark["sha256"]:
                raise SystemExit(f"{mark['file']}: sha256 {actual} does not match the manifest {mark['sha256']}")
        for mark in manifest["marks"]:
            src = source_dir / mark["file"]
            key = asset_key(prefix, mark["institutionId"])
            outputs = []
            for density, scale in DENSITIES:
                folder = OUT / f"drawable-{density}"
                folder.mkdir(parents=True, exist_ok=True)
                target = folder / f"{key}.png"
                render(src, scale, target, work)
                outputs.append({
                    "density": density,
                    "file": str(target.relative_to(ROOT)),
                    "pixels": round(TILE_DP * scale),
                    "sha256": sha256(target),
                })
            mark["assetKey"] = key
            mark["outputs"] = outputs
            total += len(outputs)
            print(f"rendered {mark['institutionId']} ({mark['backing']}) as {key}")
        manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"wrote {total} PNG files")
    return 0


if __name__ == "__main__":
    sys.exit(main())
