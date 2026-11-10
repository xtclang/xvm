#!/usr/bin/env python3
"""
Compose the IntelliJ tour's video and slides from a tour run.

A run of TourTest (the intellij-plugin's integration tests, with -PintellijTour=true) leaves tour.json and the
screens it captured, frames/NNN.png, in its run directory. This draws each step as a 1920x1080 frame, the captured
screen with its highlight, arrow and pointer, or a title card, over a caption band with the step's number, section,
title, caption, and a tag with the feature it exercises, such as textDocument/hover, and writes:

    <out>/tour-<editor>.mp4    each step held for a time that fits its caption, silent, H.264
    <out>/tour-<editor>.pptx   the title cards, a slide per section listing its steps, and the steps' frames, captions as notes
    <out>/failures.md   the steps that failed or were not captured, and the checks that failed
    <out>/frames/       the composed frames
    <out>/clips/        with a screen recording, each step's clip

A run recorded with XTC_RECORD_VIDEO=1 has screen.mp4, the whole display with its pointer, and its tour.json has the
recording's start ("screen": {"start": epoch ms, "frame": "x,y,w,h"}), each step's "start", "shot" and "end" times,
and the "actions" the tour pressed, clicked and typed. Each step of such a run is then a clip, from its start to its
captured frame, cropped to the IDE's window: the stretches where the screen does not change are dropped, as a
time-lapse of waits, what is left plays at --speed, half its own speed unless given, for a viewer to follow, and is
sped up when it would still run longer than CLIP_MAX seconds; the step's keys and clicks are a badge, and each click is
a ring where the pointer clicked. Its frame then holds, for its caption to be read. The deck keeps the frames and links
each step's clip.

Usage: compose.py [--speed <factor>] [--editor intellij|vscode] <run directory>... <out directory>

<editor> is the editor the runs toured, intellij or vscode, as their tour.json's "editor" names it, so that the two
tours' files keep apart; --editor gives it for runs recorded before tour.json named it. Runs of both are refused.

Several run directories, such as runs that each recorded some of the tour's sections, are composed in the order given,
as one tour, a step that several runs have taken from the newest of them in which it passed. The newest run that has a
section says which steps it has: an older run's step that it lacks, such as one the tour has since dropped or renamed,
is not shown. A step that failed, or was not captured, in all of them is left out, and the steps after it are numbered on, so that the video and the deck show
no error; failures.md lists each failure, with its section, its run and its error, and whether the step was shown from
an older run or left out, and the failed checks of the newest run of each section. A run is as new as its recording,
or its first timed step, or else its results.json. --speed is how fast a recording's clips play, against the speed it
was recorded at: 0.5, the default, is half speed, and 1 plays them as recorded.

Needs Pillow and python-pptx (pip install pillow python-pptx) and ffmpeg on the PATH.
"""

import argparse
import json
import re
import shutil
import subprocess
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont
from pptx import Presentation
from pptx.util import Emu

WIDTH, HEIGHT = 1920, 1080
# the editors whose tours this composes, which name the video and the deck
EDITORS = ("intellij", "vscode")
# the IDE's window is captured at this height; the caption band is under it
SCREEN = 936

BACKGROUND = (14, 20, 30)
PANEL = (21, 31, 44)
PANEL_EDGE = (44, 60, 78)
EYEBROW = (96, 214, 190)
HEADING = (240, 244, 248)
TEXT = (214, 222, 232)
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


def feature_tag(draw, feature, right, top):
    """The feature a step exercises, such as textDocument/hover, as a small tag whose right edge is at right."""
    face = font(20, mono=True)
    width = draw.textlength(feature, font=face)
    box = (right - width - 28, top, right, top + 34)
    draw.rounded_rectangle(box, radius=8, fill=PANEL, outline=EYEBROW, width=2)
    draw.text((box[0] + 14, top + 6), feature, font=face, fill=EYEBROW)
    return box[0]


def caption_band(draw, step, total):
    """The band under the screen: the step's number, section and title, the feature it exercises, and its caption."""
    label = f"{step['number']:02d} / {total}"
    title = f"{step['section']}  •  {step['title']}" if step["section"] else step["title"]
    head = font(30, "Semibold")
    draw.text((48, SCREEN + 16), label, font=head, fill=LABEL)
    draw.text((48 + draw.textlength(label, font=head) + 24, SCREEN + 16), title, font=head, fill=LABEL)
    if step.get("feature"):
        feature_tag(draw, step["feature"], WIDTH - 48, SCREEN + 16)
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
    caption_band(draw, step, total)
    return frame


# how fast a step's clip plays, against the recording, unless --speed says otherwise: slow enough to follow
SPEED = 0.5
# a step's clip runs at most this long, sped up when its recording is longer, at SPEED, once the unchanged stretches
# are dropped
CLIP_MAX = 40.0
# how long a step's frame holds after its clip, for its caption to be read
HOLD = 4.0


def badge_labels(step):
    """The keys and clicks of the step, which its badge shows: the actions logged from its start to its frame."""
    if not (step.get("start") and step.get("shot")):
        return []
    return [a["label"] for a in step.get("actions", []) if step["start"] <= a["time"] <= step["shot"]]


def badge(draw, labels):
    """The step's keys and clicks, at the right of the caption band's caption."""
    if not labels:
        return
    text = "   ".join(dict.fromkeys(labels))[:90]
    face = font(24, "Medium")
    width = draw.textlength(text, font=face)
    x, y = WIDTH - 48 - width - 24, SCREEN + 100
    draw.rounded_rectangle((x, y, x + width + 24, y + 36), radius=10, fill=PANEL, outline=HIGHLIGHT, width=2)
    draw.text((x + 12, y + 4), text, font=face, fill=HIGHLIGHT)


def length(video_file):
    """The seconds the file at [video_file] plays, as ffprobe reads them from the container."""
    probe = subprocess.run(
        ["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "default=nw=1:nk=1", str(video_file)],
        capture_output=True, text=True, check=True,
    )
    return float(probe.stdout.strip() or 0)


def clip(step, still, out, speed):
    """
    The step's clip from its run's screen recording, at [speed] and at most CLIP_MAX seconds, with its band, then its
    frame held; None without one.
    """
    screen = step.get("screen")
    if not (screen and step.get("start") and step.get("shot")) or step["shot"] <= step["start"]:
        return None
    recording = step["run"] / screen["file"]
    if not recording.exists():
        return None
    begin = (step["start"] - screen["start"]) / 1000.0
    length = (step["shot"] - step["start"]) / 1000.0 + 0.5
    x, y, w, h = [int(v) for v in (screen.get("frame") or f"0,0,{WIDTH},{SCREEN}").split(",")]
    rings = [
        f"drawbox=x={a['x'] - x - 30}:y={a['y'] - y - 30}:w=60:h=60:color=0xF0A83C@0.9:t=6"
        f":enable='between(t,{(a['time'] - step['start']) / 1000.0:.2f},{(a['time'] - step['start']) / 1000.0 + 0.6:.2f})'"
        for a in step.get("actions", [])
        if a.get("x") is not None and step["start"] <= a["time"] <= step["shot"]
    ]
    band = out / "clips" / f"band-{step['number']:03d}.png"
    still.crop((0, SCREEN, WIDTH, HEIGHT)).save(band)
    lapse = out / "clips" / f"lapse-{step['number']:03d}.mp4"
    chain = [f"crop={w}:{min(h, SCREEN)}:{x}:{y}", f"scale={WIDTH}:{SCREEN}", *rings, "mpdecimate", "setpts=N/15/TB"]
    subprocess.run(
        ["ffmpeg", "-v", "error", "-y", "-ss", f"{max(begin, 0):.2f}", "-t", f"{length:.2f}", "-i", str(recording),
         "-vf", ",".join(chain), "-r", "15", "-an", "-c:v", "libx264", "-preset", "veryfast", "-crf", "23", str(lapse)],
        check=True,
    )
    duration = length(lapse)
    stretch = min(1 / speed, CLIP_MAX / duration) if duration > 0 else 1 / speed
    held = out / "clips" / f"still-{step['number']:03d}.png"
    still.save(held)
    segment = out / "clips" / f"{step['number']:03d}.mp4"
    subprocess.run(
        ["ffmpeg", "-v", "error", "-y", "-i", str(lapse), "-i", str(band), "-loop", "1", "-t", f"{HOLD}", "-i", str(held),
         "-filter_complex",
         f"[0:v]setpts=PTS*{stretch:.4f},pad={WIDTH}:{HEIGHT}:0:0:color=0x0E141E[c];[c][1:v]overlay=0:{SCREEN}[v];"
         f"[2:v]scale={WIDTH}:{HEIGHT},setpts=PTS-STARTPTS[s];[v][s]concat=n=2:v=1:a=0,fps=15,format=yuv420p[o]",
         "-map", "[o]", "-c:v", "libx264", "-preset", "veryfast", "-crf", "23", str(segment)],
        check=True,
    )
    lapse.unlink()
    return segment


def held(step, still, out):
    """A step without a clip, or a card: its frame held for its step's time."""
    segment = out / "clips" / f"{step['number']:03d}.mp4"
    frame = out / "clips" / f"still-{step['number']:03d}.png"
    still.save(frame)
    subprocess.run(
        ["ffmpeg", "-v", "error", "-y", "-loop", "1", "-t", f"{seconds(step):.2f}", "-i", str(frame),
         "-vf", "fps=15,format=yuv420p", "-c:v", "libx264", "-preset", "veryfast", "-crf", "23", "-tune", "stillimage",
         str(segment)],
        check=True,
    )
    return segment


def clips_video(segments, out, movie):
    """The steps' segments, clips and held frames, one after another, as one video, [movie]."""
    listing = out / "clips.txt"
    listing.write_text("".join(f"file '{segment.resolve()}'\n" for segment in segments))
    subprocess.run(
        ["ffmpeg", "-v", "error", "-y", "-f", "concat", "-safe", "0", "-i", str(listing), "-c", "copy",
         "-movflags", "+faststart", str(movie)],
        check=True,
    )
    listing.unlink()


def seconds(step):
    """How long the video holds a step: long enough to read its caption, as a viewer would."""
    words = len(step["caption"].split()) + (8 if step.get("card") else 0)
    return min(max(6.0 + words * 0.32, 9.0), 16.0)


def video(frames, steps, out, movie):
    """The frames, each held for its step's time, as an H.264 video at 15 frames a second, [movie]."""
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
            "-tune", "stillimage", "-movflags", "+faststart", str(movie),
        ],
        check=True,
    )
    listing.unlink()


def slides(frames, steps, out, path):
    """The deck, [path]: the title cards, then per section a slide listing its steps and its first captured step."""
    deck = Presentation()
    deck.slide_width, deck.slide_height = Emu(12192000), Emu(6858000)
    blank = deck.slide_layouts[6]

    def picture(frame, notes, movie=None):
        slide = deck.slides.add_slide(blank)
        slide.shapes.add_picture(str(frame), 0, 0, width=deck.slide_width, height=deck.slide_height)
        if movie:
            # the step's clip over its frame, which is its poster
            slide.shapes.add_movie(str(movie), 0, 0, deck.slide_width, deck.slide_height, poster_frame_image=str(frame),
                                   mime_type="video/mp4")
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
            for frame, step in shots if any(step.get("clip") for _, step in shots) else shots[:1]:
                picture(frame, step["caption"], step.get("clip"))
    deck.save(str(path))


def passed(step):
    """Whether the step can be shown: a card, or a captured screen, of a step that did not fail."""
    if step.get("error"):
        return False
    return bool(step.get("card")) or bool(step.get("image")) and (step["run"] / step["image"]).exists()


def recorded_at(run, tour):
    """When the run was recorded, in seconds, to rank runs by: its recording's start, its first timed step's, else when
    its results were written."""
    times = [(tour.get("screen") or {}).get("start"), *(step.get("start") for step in tour["steps"])]
    first = next((time for time in times if time), None)
    if first:
        return first / 1000.0
    written = [run / name for name in ("results.json", "tour.json") if (run / name).exists()]
    return written[0].stat().st_mtime if written else 0.0


def keyed(steps):
    """The steps by their section, title and how many steps of that section and title came before them in the run."""
    seen = {}
    for step in steps:
        name = (step.get("section") or "", step["title"])
        seen[name] = seen.get(name, -1) + 1
        yield (*name, seen[name]), step


def tour_order(runs):
    """
    The steps' keys in tour order: each run's steps in their order, merged; a step only some runs have goes after the
    step before it in its run, else before the next step of its run that an earlier run had, else at the end of its
    section, else at the end.
    """
    order = []
    for keys in runs:
        for i, key in enumerate(keys):
            if key in order:
                continue
            if i > 0:
                position = order.index(keys[i - 1]) + 1
            else:
                later = next((order.index(k) for k in keys[1:] if k in order), None)
                section = [j + 1 for j, k in enumerate(order) if k[0] == key[0]]
                position = later if later is not None else section[-1] if section else len(order)
            order.insert(position, key)
    return order


def one_line(text):
    return " ".join(str(text).split())


def failed_checks(tours):
    """The failed checks of the newest run of each section: from its tour.json, or, before tour.json had them, from its
    results.json."""
    newest = {}
    for run, tour, at in tours:
        for step in tour["steps"]:
            section = step.get("section") or ""
            if section and (section not in newest or newest[section][2] < at):
                newest[section] = (run, tour, at)
    checks = []
    for section, (run, tour, _) in newest.items():
        if "checks" in tour:
            checks += [(section, run, f"{check['what']}: {check['error']}")
                       for check in tour["checks"] if check.get("section") == section and check.get("error")]
        elif (run / "results.json").exists():
            # results.json lists the failed steps too, as "section: number title: error", which the steps' list has
            titles = [f"{step['title']}: " for step in tour["steps"] if step.get("section") == section]
            failed = json.loads((run / "results.json").read_text()).get("failed") or []
            checks += [(section, run, line[len(section) + 2:]) for line in failed
                       if line.startswith(section + ": ")
                       and not any(re.sub(r"^\d+ ", "", line[len(section) + 2:]).startswith(title) for title in titles)]
    return checks


def failures_report(failures, checks, out):
    """failures.md: the steps that failed or were not captured, and the checks that failed."""
    lines = ["# Failures", "",
             "The steps that failed or were not captured, newest run first, and what the video and the deck show "
             "instead; then the failed checks of the newest run of each section.", "", "## Steps", ""]
    for step, shown in failures:
        outcome = f"Shown from {shown['run'].name}" if shown else "Left out"
        error = one_line(step.get("error") or "not captured")
        detail = f" (check: {one_line(step['check'])})" if step.get("check") else ""
        lines.append(f"- {step.get('section') or 'Start'}, \"{step['title']}\" ({step['run'].name}): {error}{detail}. "
                     f"{outcome}.")
    if not failures:
        lines.append("None.")
    lines += ["", "## Checks", ""]
    lines += [f"- {section} ({run.name}): {one_line(failure)}" for section, run, failure in checks]
    if not checks:
        lines.append("None.")
    (out / "failures.md").write_text("\n".join(lines) + "\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--speed", type=float, default=SPEED, help="how fast clips play against the recording")
    parser.add_argument("--editor", choices=EDITORS,
                        help="the editor the runs toured, for runs whose tour.json does not name it")
    parser.add_argument("paths", nargs="+", metavar="directory", help="the run directories, then the out directory")
    arguments = parser.parse_args()
    if len(arguments.paths) < 2 or arguments.speed <= 0:
        sys.exit(__doc__)
    runs, out = [Path(arg) for arg in arguments.paths[:-1]], Path(arguments.paths[-1])
    tours = []
    candidates = {}
    for run in runs:
        tour = json.loads((run / "tour.json").read_text())
        at = recorded_at(run, tour)
        tours.append((run, tour, at))
        tagged = [{**step, "run": run, "screen": tour.get("screen"), "actions": tour.get("actions") or []}
                  for step in tour["steps"]]
        for key, step in keyed(tagged):
            candidates.setdefault(key, []).append((at, step))
    named = {tour["editor"] for _, tour, _ in tours if tour.get("editor")}
    editor = arguments.editor or (named.pop() if len(named) == 1 else None)
    if editor not in EDITORS or named - {editor}:
        sys.exit(f"The runs name the editors {sorted(named)}: compose one editor's runs, and give --editor "
                 f"{' or '.join(EDITORS)} for runs whose tour.json names none")
    movie, deck = out / f"tour-{editor}.mp4", out / f"tour-{editor}.pptx"
    out.mkdir(parents=True, exist_ok=True)
    steps = []
    failures = []
    left = 0
    keys = [[key for key, _ in keyed(tour["steps"])] for _, tour, _ in tours]
    newest = {}
    for (_, _, at), run_keys in zip(tours, keys):
        for section in {key[0] for key in run_keys}:
            if section not in newest or newest[section][0] < at:
                newest[section] = (at, set(run_keys))
    for key in [key for key in tour_order(keys) if key in newest[key[0]][1]]:
        ranked = [step for _, step in sorted(candidates[key], key=lambda candidate: candidate[0], reverse=True)]
        shown = next((step for step in ranked if passed(step)), None)
        failures += [(step, shown) for step in (ranked[:ranked.index(shown)] if shown else ranked)]
        if shown:
            steps.append({**shown, "number": len(steps) + 1})
        else:
            left += 1
    for directory in ("frames", "clips"):
        if (out / directory).exists():
            shutil.rmtree(out / directory)
        (out / directory).mkdir(parents=True)
    frames = []
    segments = []
    recorded = any(step.get("screen") for step in steps)
    for step in steps:
        path = out / "frames" / f"{step['number']:03d}.png"
        still = compose(step, len(steps))
        badge(ImageDraw.Draw(still), badge_labels(step))
        still.save(path)
        frames.append(path)
        if recorded:
            step["clip"] = clip(step, still, out, arguments.speed)
            segments.append(step["clip"] or held(step, still, out))
    if recorded:
        clips_video(segments, out, movie)
    else:
        video(frames, steps, out, movie)
    slides(frames, steps, out, deck)
    checks = failed_checks(tours)
    failures_report(failures, checks, out)
    # of the written video: the clips' stretched and held lengths are not the steps' reading times
    total = length(movie)
    print(f"{len(steps)} steps, {total / 60:.1f} minutes; {len(failures)} failed steps, {left} steps left out, "
          f"{len(checks)} failed checks: {out / 'failures.md'}")


if __name__ == "__main__":
    main()
