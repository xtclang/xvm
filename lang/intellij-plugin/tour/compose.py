#!/usr/bin/env python3
"""
Compose the IntelliJ tour's video and slides from a tour run.

A run of TourTest (the intellij-plugin's integration tests, with -PintellijTour=true) leaves tour.json and the
screens it captured, frames/NNN.png, in its run directory. This draws each step as a 1920x1080 frame, the captured
screen with its highlight, arrow and pointer, or a title card, over a caption band with the step's number, section,
title and caption, and writes:

    <out>/tour.mp4    each step held for a time that fits its caption, silent, H.264
    <out>/tour.pptx   the title cards, a slide per section listing its steps, and the steps' frames, captions as notes
    <out>/frames/     the composed frames

Usage: compose.py <run directory>... <out directory>

Several run directories, such as runs that each recorded some of the tour's sections, are composed in the order given,
as one tour.

Needs Pillow and python-pptx (pip install pillow python-pptx) and ffmpeg on the PATH.
"""

import json
import shutil
import subprocess
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont
from pptx import Presentation
from pptx.util import Emu

WIDTH, HEIGHT = 1920, 1080
# the IDE's window is captured at this height; the caption band is under it
SCREEN = 936

BACKGROUND = (14, 20, 30)
PANEL = (21, 31, 44)
PANEL_EDGE = (44, 60, 78)
EYEBROW = (96, 214, 190)
HEADING = (240, 244, 248)
TEXT = (214, 222, 232)
MUTED = (150, 162, 176)
LABEL = (218, 228, 160)
HIGHLIGHT = (240, 168, 60)
CODE = (190, 226, 210)

FONT = "/System/Library/Fonts/SFNS.ttf"
MONO = "/System/Library/Fonts/SFNSMono.ttf"
FALLBACK = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
FALLBACK_MONO = "/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf"


def font(size, weight="Regular", mono=False):
    """The system font at a size and weight, or DejaVu where SF is not installed."""
    path = MONO if mono else FONT
    if not Path(path).exists():
        return ImageFont.truetype(FALLBACK_MONO if mono else FALLBACK, size)
    face = ImageFont.truetype(path, size)
    try:
        face.set_variation_by_name(weight)
    except (OSError, ValueError):
        pass
    return face


def tracked(draw, xy, text, face, fill, tracking=4):
    """Text with extra space between its letters, as small capitals are set."""
    x, y = xy
    for char in text:
        draw.text((x, y), char, font=face, fill=fill)
        x += draw.textlength(char, font=face) + tracking


def wrap(draw, text, face, width):
    """The lines of text that fit in width."""
    lines, line = [], ""
    for word in text.split():
        candidate = f"{line} {word}".strip()
        if draw.textlength(candidate, font=face) <= width or not line:
            line = candidate
        else:
            lines.append(line)
            line = word
    if line:
        lines.append(line)
    return lines


def caption_band(draw, step, total):
    """The band under the screen: the step's number, section and title, and its caption."""
    label = f"{step['number']:02d} / {total}"
    title = f"{step['section']}  •  {step['title']}" if step["section"] else step["title"]
    head = font(30, "Semibold")
    draw.text((48, SCREEN + 16), label, font=head, fill=LABEL)
    draw.text((48 + draw.textlength(label, font=head) + 24, SCREEN + 16), title, font=head, fill=LABEL)
    body = font(30)
    for i, line in enumerate(wrap(draw, step["caption"], body, WIDTH - 96)[:2]):
        draw.text((48, SCREEN + 60 + i * 38), line, font=body, fill=TEXT)


def card(draw, content):
    """A title card: eyebrow, heading, lead, boxes side by side, and a block of code."""
    x, y = 110, 72
    tracked(draw, (x, y), content["eyebrow"], font(20, "Medium"), EYEBROW)
    y += 52
    draw.text((x, y), content["heading"], font=font(66, "Bold"), fill=HEADING)
    y += 106
    if content.get("lead"):
        draw.text((x, y), content["lead"], font=font(30), fill=TEXT)
        y += 64
    boxes = content.get("boxes") or []
    if boxes:
        gap = 24
        width = (WIDTH - 2 * x - gap * (len(boxes) - 1)) // len(boxes)
        body = font(26)
        height = 40 + 50 + max(len(wrap(draw, box["text"], body, width - 60)) for box in boxes) * 36 + 24
        for i, box in enumerate(boxes):
            left = x + i * (width + gap)
            draw.rounded_rectangle((left, y, left + width, y + height), radius=10, fill=PANEL, outline=PANEL_EDGE, width=2)
            draw.text((left + 30, y + 30), box["title"], font=font(32, "Semibold"), fill=EYEBROW)
            for j, line in enumerate(wrap(draw, box["text"], body, width - 60)):
                draw.text((left + 30, y + 84 + j * 36), line, font=body, fill=TEXT)
        y += height + 40
    if content.get("code"):
        mono = font(26, mono=True)
        lines = content["code"].split("\n")
        height = 40 + len(lines) * 38
        draw.rounded_rectangle((x, y, WIDTH - x, y + height), radius=10, fill=PANEL, outline=PANEL_EDGE, width=2)
        for i, line in enumerate(lines):
            draw.text((x + 30, y + 22 + i * 38), line, font=mono, fill=CODE)


def arrow(draw, start, end):
    """An arrow from start to the edge of the highlight at end."""
    draw.line((start, end), fill=HIGHLIGHT, width=5)
    (x0, y0), (x1, y1) = start, end
    length = max(((x1 - x0) ** 2 + (y1 - y0) ** 2) ** 0.5, 1)
    ux, uy = (x1 - x0) / length, (y1 - y0) / length
    size = 22
    left = (x1 - ux * size - uy * size * 0.6, y1 - uy * size + ux * size * 0.6)
    right = (x1 - ux * size + uy * size * 0.6, y1 - uy * size - ux * size * 0.6)
    draw.polygon((end, left, right), fill=HIGHLIGHT)


def highlight(draw, focus, cursor):
    """The step's subject: an outline around it, an arrow to a small one, and the pointer on it."""
    if focus:
        x, y, w, h = focus["x"], focus["y"], focus["width"], focus["height"]
        pad = 6
        box = (max(x - pad, 2), max(y - pad, 2), min(x + w + pad, WIDTH - 3), min(y + h + pad, SCREEN - 3))
        draw.rounded_rectangle(box, radius=8, outline=HIGHLIGHT, width=4)
        # a small subject gets an arrow, from the side of the screen with more room
        if w * h < 120_000:
            cx, cy = (box[0] + box[2]) / 2, (box[1] + box[3]) / 2
            if cx > WIDTH / 2:
                start, end = (box[0] - 170, cy + 110), (box[0] - 6, cy + 8)
            else:
                start, end = (box[2] + 170, cy + 110), (box[2] + 6, cy + 8)
            start = (min(max(start[0], 20), WIDTH - 20), min(max(start[1], 20), SCREEN - 20))
            arrow(draw, start, end)
    if cursor:
        cx, cy = cursor["x"], cursor["y"]
        draw.ellipse((cx - 14, cy - 14, cx + 14, cy + 14), outline=HIGHLIGHT, width=3)


def compose(step, total):
    """The step's frame: its screen with its highlight, or its card, over the caption band."""
    frame = Image.new("RGB", (WIDTH, HEIGHT), BACKGROUND)
    draw = ImageDraw.Draw(frame)
    if step.get("image"):
        screen = Image.open(step["run"] / step["image"]).convert("RGB")
        if screen.size != (WIDTH, SCREEN):
            screen = screen.resize((WIDTH, SCREEN), Image.LANCZOS)
        frame.paste(screen, (0, 0))
        highlight(draw, step.get("focus"), step.get("cursor"))
    elif step.get("card"):
        card(draw, step["card"])
    else:
        # a step that failed to capture: say so on the frame, so a draft shows what is missing
        draw.text((110, 400), f"Not captured: {step.get('error', 'no screen')}"[:120], font=font(30), fill=MUTED)
    caption_band(draw, step, total)
    return frame


def seconds(step):
    """How long the video holds a step: long enough to read its caption, as a viewer would."""
    words = len(step["caption"].split()) + (8 if step.get("card") else 0)
    return min(max(6.0 + words * 0.32, 9.0), 16.0)


def video(frames, steps, out):
    """The frames, each held for its step's time, as an H.264 video at 15 frames a second."""
    listing = out / "frames.txt"
    with listing.open("w") as entries:
        for frame, step in zip(frames, steps):
            entries.write(f"file '{frame.resolve()}'\nduration {seconds(step):.2f}\n")
        # the concat demuxer holds the last frame only if it is listed again
        entries.write(f"file '{frames[-1].resolve()}'\n")
    subprocess.run(
        [
            "ffmpeg", "-v", "error", "-y", "-f", "concat", "-safe", "0", "-i", str(listing),
            "-vf", "fps=15,format=yuv420p", "-c:v", "libx264", "-preset", "slow", "-crf", "18",
            "-tune", "stillimage", "-movflags", "+faststart", str(out / "tour.mp4"),
        ],
        check=True,
    )
    listing.unlink()


def slides(frames, steps, out):
    """The deck: the title cards, then per section a slide listing its steps and its first captured step."""
    deck = Presentation()
    deck.slide_width, deck.slide_height = Emu(12192000), Emu(6858000)
    blank = deck.slide_layouts[6]

    def picture(frame, notes):
        slide = deck.slides.add_slide(blank)
        slide.shapes.add_picture(str(frame), 0, 0, width=deck.slide_width, height=deck.slide_height)
        slide.notes_slide.notes_text_frame.text = notes

    sections = []
    for frame, step in zip(frames, steps):
        if step.get("card") or not sections or sections[-1][0] != step["section"]:
            sections.append((step["section"], []))
        sections[-1][1].append((frame, step))
    for section, members in sections:
        cards = [(frame, step) for frame, step in members if step.get("card")]
        shots = [(frame, step) for frame, step in members if step.get("image")]
        for frame, step in cards:
            picture(frame, step["caption"])
        if shots:
            overview = Image.new("RGB", (WIDTH, HEIGHT), BACKGROUND)
            draw = ImageDraw.Draw(overview)
            tracked(draw, (110, 72), section.upper(), font(20, "Medium"), EYEBROW)
            draw.text((110, 124), section, font=font(66, "Bold"), fill=HEADING)
            listed = [step for _, step in members if not step.get("card")]
            # a long section takes two columns, so that its list stays above the caption band
            rows = 14 if len(listed) > 14 else len(listed)
            for i, step in enumerate(listed):
                x, y = 110 + (i // rows) * 860, 260 + (i % rows) * 46
                draw.text((x, y), f"{step['number']:02d}   {step['title']}", font=font(30), fill=TEXT)
            path = out / "frames" / f"section-{len(deck.slides._sldIdLst):03d}.png"
            overview.save(path)
            picture(path, "\n".join(f"{step['number']:02d} {step['title']}: {step['caption']}" for _, step in members))
            frame, step = shots[0]
            picture(frame, step["caption"])
    deck.save(str(out / "tour.pptx"))


def main():
    if len(sys.argv) < 3:
        sys.exit(__doc__)
    runs, out = [Path(arg) for arg in sys.argv[1:-1]], Path(sys.argv[-1])
    steps = []
    for run in runs:
        for step in json.loads((run / "tour.json").read_text())["steps"]:
            steps.append({**step, "run": run, "number": len(steps) + 1})
    if (out / "frames").exists():
        shutil.rmtree(out / "frames")
    (out / "frames").mkdir(parents=True)
    frames = []
    for step in steps:
        path = out / "frames" / f"{step['number']:03d}.png"
        compose(step, len(steps)).save(path)
        frames.append(path)
    video(frames, steps, out)
    slides(frames, steps, out)
    failed = [step for step in steps if step.get("error")]
    total = sum(seconds(step) for step in steps)
    print(f"{len(steps)} steps, {total / 60:.1f} minutes; {len(failed)} not captured")
    for step in failed:
        print(f"  {step['number']:02d} {step['title']}: {step['error']}")


if __name__ == "__main__":
    main()
