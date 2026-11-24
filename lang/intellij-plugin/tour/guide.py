#!/usr/bin/env python3
"""
Rebuild the user guide's screenshots, lang/doc/user-guide/*.png, from tour runs.

A tour step that a guide image comes from names it in its tour.json entry, with the part of the frame to keep, as
ImageMagick geometry on a 1920x936 frame: "guide": {"name": "intellij-completion", "crop": "1205x815+675+85"}, or no
crop for the whole frame (GuideImage in the IntelliJ tour's TourScript.kt, Shot.guide in the VS Code tour's script.ts).
For each such step of the given runs that captured its frame, this crops the frame, scaled when the frame has another
size, scales the result to at most 1280 px wide and writes it as a 256-color PNG, <out>/<name>.png. The images of
steps that failed, and of sections a run did not record, are left as they were.

It then compares the images the tours name with those that user-guide.md, beside <out>, shows: an image the guide
shows that no step of these runs names is listed, as it was not refreshed; with runs of both tours that record every
section, that means no step makes it any more.

Usage: guide.py [--out <directory>] <run directory>...

A run directory is IntelliJ's lang/intellij-plugin/build/reports/compiler-playbook/run-tour-* or VS Code's
lang/vscode-extension/build/reports/vscode-tour/run-*. Needs ImageMagick, magick or convert, on the PATH. Exits with 1
when a step that names an image has no frame, or two steps name the same image.
"""

import argparse
import json
import re
import shutil
import struct
import subprocess
import sys
from pathlib import Path

# the frame size that the steps' crops are given on, the tours' 1920x936
FRAME = (1920, 936)
WIDTH = 1280
GEOMETRY = re.compile(r"(\d+)x(\d+)\+(\d+)\+(\d+)")
GUIDE = Path(__file__).resolve().parents[2] / "doc" / "user-guide"


def imagemagick() -> str:
    for tool in ("magick", "convert"):
        if shutil.which(tool):
            return tool
    sys.exit("guide.py needs ImageMagick: magick or convert on the PATH")


def size(png: Path) -> tuple[int, int]:
    """A PNG's width and height, from its header."""
    with png.open("rb") as file:
        header = file.read(24)
    return struct.unpack(">II", header[16:24])


def crop(geometry: str, frame: tuple[int, int]) -> str:
    """[geometry] on a FRAME-sized frame, for a frame of size [frame]."""
    match = GEOMETRY.fullmatch(geometry)
    if not match:
        raise ValueError(f"not a crop geometry WxH+X+Y: {geometry}")
    width, height, x, y = (int(group) for group in match.groups())
    sx, sy = frame[0] / FRAME[0], frame[1] / FRAME[1]
    return f"{round(width * sx)}x{round(height * sy)}+{round(x * sx)}+{round(y * sy)}"


def write(tool: str, frame: Path, geometry: str | None, out: Path) -> None:
    arguments = [tool, str(frame)]
    if geometry:
        arguments += ["-crop", crop(geometry, size(frame)), "+repage"]
    arguments += [
        "-resize", f"{WIDTH}x>", "-strip", "-dither", "None", "-colors", "256",
        "-define", "png:compression-level=9", "-define", "png:compression-filter=5", f"PNG8:{out}",
    ]
    subprocess.run(arguments, check=True)


def shown(guide: Path) -> set[str]:
    """The images that the guide's markdown shows, by name."""
    text = guide.read_text(encoding="utf-8") if guide.is_file() else ""
    return set(re.findall(r"user-guide/([A-Za-z0-9_.-]+)\.png", text))


def main() -> int:
    parser = argparse.ArgumentParser(description="Rebuild the user guide's screenshots from tour runs.")
    parser.add_argument("--out", type=Path, default=GUIDE, help=f"where the images go, by default {GUIDE}")
    parser.add_argument("runs", nargs="+", type=Path, help="tour run directories, with tour.json and frames/")
    options = parser.parse_args()
    tool = imagemagick()
    options.out.mkdir(parents=True, exist_ok=True)
    named: dict[str, str] = {}
    problems: list[str] = []
    for run in options.runs:
        steps = json.loads((run / "tour.json").read_text(encoding="utf-8"))["steps"]
        for step in steps:
            guide = step.get("guide")
            if not guide:
                continue
            name, where = guide["name"], f"{run.name}: {step['number']} {step['title']}"
            if name in named:
                problems.append(f"{name}: named by {named[name]} and by {where}")
                continue
            named[name] = where
            frame = run / step["image"] if step.get("image") else None
            if frame is None or not frame.is_file() or step.get("error"):
                problems.append(f"{name}: {where} has no frame ({step.get('error') or 'not captured'})")
                continue
            out = options.out / f"{name}.png"
            write(tool, frame, guide.get("crop"), out)
            width, height = size(out)
            print(f"{out.name}: {width}x{height}, {out.stat().st_size // 1024} KB, from {where}")
    stale = sorted(shown(options.out.parent / "user-guide.md") - set(named))
    if stale:
        print(f"not refreshed, no step of these runs names them: {', '.join(stale)}")
    for problem in problems:
        print(f"problem: {problem}", file=sys.stderr)
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
