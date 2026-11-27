"""Local speech, segment timing and subtitles for the editor tour composer."""

import hashlib
import json
import math
import os
import re
import shutil
import subprocess
import sys
import tempfile
import textwrap
import wave
from array import array
from dataclasses import dataclass
from pathlib import Path
from xml.sax.saxutils import escape

FPS = 15
RATE = 48000
PARAGRAPH_PAUSE = 1.0
SENTENCE_PAUSE = 0.75
PIPER_LENGTH_SCALE = 1.07


def read_script(path):
    """Index spoken paragraphs by (section, title), using None for an introduction."""
    entries = {}
    key = None
    # The script header describes the format and is never spoken.
    source = path.read_text(encoding="utf-8")
    source = re.sub(r"<!--.*?-->", "", source, flags=re.S)
    for line in source.splitlines():
        heading = re.fullmatch(r"(#{2,3})\s+(.+)", line.strip())
        if heading:
            level, name = heading.groups()
            if level == "##":
                key = (name, None)
            elif key:
                key = (key[0], name)
            else:
                raise ValueError(f"{path}: step before a section: {name}")
            if key in entries:
                raise ValueError(f"{path}: duplicate heading: {key}")
            entries[key] = []
        elif key and line.strip():
            entries[key].append(line.strip())
    return {key: " ".join(lines) for key, lines in entries.items()}


def match(script, steps):
    """Attach a section intro to its first narrated step. A missing step stays silent."""
    used = set()
    paragraphs = []
    warnings = []
    for step in steps:
        section, title = step.get("section", ""), step["title"]
        intro, key = (section, None), (section, title)
        spoken = []
        if script.get(key):
            if intro not in used and script.get(intro):
                spoken.append(script[intro])
                used.add(intro)
            spoken.append(script[key])
            used.add(key)
        else:
            warnings.append(f"No narration for step: {section} / {title}")
        paragraphs.append(spoken)
    for key, paragraph in script.items():
        if paragraph and key not in used:
            warnings.append("Unmatched narration: " + " / ".join(part for part in key if part))
    return paragraphs, warnings


@dataclass(frozen=True)
class Paragraph:
    text: str
    audio: Path
    samples: int

    @property
    def seconds(self):
        return self.samples / RATE


def spoken_text(text):
    """Pronounce the project name in prose, shell commands, and Gradle task names."""
    text = re.sub(r"(?<=[a-z])Xtc\b", " Ecstasy", text)
    return re.sub(r"\bxtc\b", "Ecstasy", text, flags=re.IGNORECASE)


class SpeechCache:
    def __init__(self, directory, identity, synthesize):
        self.directory = directory
        self.identity = identity
        self.synthesize = synthesize

    def paragraph(self, text):
        spoken = spoken_text(text)
        key = hashlib.sha256(json.dumps([self.identity, spoken]).encode()).hexdigest()
        self.directory.mkdir(parents=True, exist_ok=True)
        audio = self.directory / f"{key}.wav"
        if not audio.exists():
            # Publish only a complete, validated file, even when two composers share the cache.
            with tempfile.TemporaryDirectory(dir=self.directory) as tmp:
                result = Path(tmp) / "normalized.wav"
                self.synthesize(spoken, result)
                self.samples(result)
                try:
                    os.link(result, audio)
                except FileExistsError:
                    # Another process published its take first. Its duration and audio must stay paired.
                    pass
        return Paragraph(text, audio, self.samples(audio))

    @staticmethod
    def samples(path):
        with wave.open(str(path)) as wav:
            if ((wav.getframerate(), wav.getnchannels(), wav.getsampwidth()) != (RATE, 1, 2)
                    or not wav.getnframes()):
                raise ValueError(f"No usable 48 kHz mono speech in {path}")
            return wav.getnframes()


def speech_cache(engine, voice, model, sentence_pause=SENTENCE_PAUSE):
    executable = shutil.which(engine)
    if not executable:
        raise ValueError(f"The local {engine} speech executable is missing from PATH")
    if engine == "piper":
        if not model or not model.is_file() or not Path(str(model) + ".json").is_file():
            raise ValueError("Piper needs --piper-model pointing to a local .onnx file with its .onnx.json beside it")
        model = model.resolve()
        identity = [str(model), model.stat().st_mtime_ns, Path(str(model) + ".json").stat().st_mtime_ns,
                    PIPER_LENGTH_SCALE, sentence_pause]
    else:
        identity = [voice, 160, sentence_pause]

    def synthesize(text, wav):
        if engine == "say":
            raw = wav.with_suffix(".aiff")
            command = [executable, "-v", voice, "-r", "160", "-o", str(raw)]
            if sentence_pause:
                text = re.sub(r"(?<=[.!?])\s+", f" [[slnc {round(1000 * sentence_pause)}]] ", text)
        else:
            raw = wav.with_name("piper.wav")
            command = [executable, "-m", str(model), "-f", str(raw), "--sentence-silence", str(sentence_pause),
                       "--length-scale", str(PIPER_LENGTH_SCALE)]
        subprocess.run(command, input=text + "\n", text=True, check=True)
        subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", str(raw), "-af", "loudnorm=I=-16:TP=-1.5:LRA=7",
                        "-ar", str(RATE), "-ac", "1", "-c:a", "pcm_s16le", str(wav)], check=True)

    root = Path(os.environ.get("XDG_CACHE_HOME") or Path.home() / ".cache")
    return SpeechCache(root / "xtc-tour" / "speech", [1, executable, identity], synthesize)


def segment_frames(visual_seconds, paragraphs, pad):
    speech = sum(p.samples for p in paragraphs) / RATE + PARAGRAPH_PAUSE * max(0, len(paragraphs) - 1)
    visual_frames = math.ceil(round(FPS * visual_seconds, 9))
    return max(visual_frames, math.ceil(FPS * (speech + pad)) if paragraphs else 0)


@dataclass(frozen=True)
class Cue:
    start: float
    end: float
    text: str


def sentence_spans(paragraph, sentences):
    """Use the recorded pauses when they identify the sentence boundaries unambiguously."""
    if len(sentences) == 1:
        return [(0, paragraph.seconds)]
    gaps = []
    quiet = None
    window = RATE // 100
    with wave.open(str(paragraph.audio)) as wav:
        for offset in range(0, paragraph.samples, window):
            pcm = array("h", wav.readframes(window))
            if sys.byteorder != "little":
                pcm.byteswap()
            if max(map(abs, pcm), default=0) < 160:
                if quiet is None:
                    quiet = offset
            elif quiet is not None:
                if quiet and offset - quiet >= RATE * 0.35:
                    gaps.append((quiet / RATE, offset / RATE))
                quiet = None
    if len(gaps) == len(sentences) - 1:
        return list(zip([0, *(end for _, end in gaps)], [*(begin for begin, _ in gaps), paragraph.seconds]))
    # Abbreviations and punctuation can split differently in a speech engine.
    total = sum(map(len, sentences))
    elapsed = 0
    spans = []
    for sentence in sentences:
        start = paragraph.seconds * elapsed / total
        elapsed += len(sentence)
        spans.append((start, paragraph.seconds * elapsed / total))
    return spans


def paragraph_cues(paragraphs, start=0):
    """Keep paragraph and sentence timing, dividing long sentences into two-line cues."""
    cues = []
    for paragraph in paragraphs:
        sentences = re.split(r"(?<=[.!?])\s+", paragraph.text)
        for sentence, (begin, end) in zip(sentences, sentence_spans(paragraph, sentences)):
            lines = textwrap.wrap(sentence, width=42, break_on_hyphens=False)
            chunks = ["\n".join(lines[i:i + 2]) for i in range(0, len(lines), 2)]
            total = sum(map(len, chunks))
            count = 0
            for chunk in chunks:
                onset = begin + (end - begin) * count / total
                count += len(chunk)
                cues.append(Cue(start + onset, start + begin + (end - begin) * count / total, chunk))
        start += paragraph.seconds + PARAGRAPH_PAUSE
    return cues


def timestamp(seconds, decimal):
    seconds, millis = divmod(round(seconds * 1000), 1000)
    minutes, seconds = divmod(seconds, 60)
    hours, minutes = divmod(minutes, 60)
    return f"{hours:02}:{minutes:02}:{seconds:02}{decimal}{millis:03}"


def write_subtitles(movie, cues):
    for suffix, decimal, header in ((".srt", ",", ""), (".vtt", ".", "WEBVTT\n\n")):
        blocks = []
        for number, cue in enumerate(cues, 1):
            clock = f"{timestamp(cue.start, decimal)} --> {timestamp(cue.end, decimal)}"
            blocks.append(f"{number}\n{clock}\n{escape(cue.text)}\n\n")
        movie.with_suffix(suffix).write_text(header + "".join(blocks), encoding="utf-8")


def subtitle_strip(cues, directory, width, face):
    """A timed transparent image strip for ffmpeg builds with or without libass."""
    from PIL import Image, ImageDraw

    height = 130
    blank = Image.new("RGBA", (width, height))
    stream = []
    end = 0.0
    for i, cue in enumerate(cues):
        if cue.start > end + 0.001:
            stream.extend(["file 'blank.png'", "option framerate 1000", f"duration {cue.start - end:.9f}"])
        end = cue.end
        strip = blank.copy()
        draw = ImageDraw.Draw(strip)
        # Center both lines above the existing caption band.
        x, y = width // 2, height // 2
        bounds = draw.multiline_textbbox((x, y), cue.text, font=face, anchor="mm", align="center", spacing=6)
        draw.rounded_rectangle((bounds[0] - 18, bounds[1] - 8, bounds[2] + 18, bounds[3] + 8),
                               radius=9, fill=(8, 13, 21, 235))
        draw.multiline_text((x, y), cue.text, font=face, anchor="mm", align="center", fill="white", spacing=6)
        strip.save(directory / f"{i}.png")
        stream.extend([f"file '{i}.png'", "option framerate 1000", f"duration {cue.end - cue.start:.9f}"])
    blank.save(directory / "blank.png")
    stream.extend(["file 'blank.png'", "option framerate 1000"])
    listing = directory / "strip.txt"
    listing.write_text("\n".join(stream) + "\n")
    return listing


def render_segment(frame, clip, visual_seconds, paragraphs, pad, output, burn=None):
    """Keep the clip's motion intact, extending its last frame for any remaining speech."""
    frames = segment_frames(visual_seconds, paragraphs, pad)
    duration = frames / FPS
    if clip:
        source = ["-i", str(clip)]
        extra = frames - round(FPS * visual_seconds)
        video_filter = f"tpad=stop_mode=clone:stop={extra}" if extra else ""
    else:
        source = ["-loop", "1", "-framerate", str(FPS), "-i", str(frame)]
        video_filter = ""
    inputs = [arg for p in paragraphs for arg in ("-i", str(p.audio))]
    if paragraphs:
        filters = [f"[{i}:a]apad=pad_dur={PARAGRAPH_PAUSE}[p{i}]" for i in range(1, len(paragraphs))]
        channels = "".join(f"[p{i}]" for i in range(1, len(paragraphs))) + f"[{len(paragraphs)}:a]"
        join = f"concat=n={len(paragraphs)}:v=0:a=1," if len(paragraphs) > 1 else ""
        filters.append(f"{channels}{join}apad[a]")
    else:
        inputs = ["-f", "lavfi", "-i", f"anullsrc=r={RATE}:cl=mono"]
        filters = []
    with tempfile.TemporaryDirectory(dir=output.parent) as tmp:
        video = "0:v"
        if video_filter:
            filters.append(f"[0:v]{video_filter}[held]")
            video = "[held]"
        if burn and paragraphs:
            width, bottom, face = burn
            strip = subtitle_strip(paragraph_cues(paragraphs), Path(tmp), width, face)
            inputs.extend(["-f", "concat", "-safe", "0", "-i", str(strip)])
            base = "[0:v]" if video == "0:v" else video
            filters.append(f"{base}[{len(paragraphs) + 1}:v]overlay=0:{bottom}-overlay_h-12[subbed]")
            video = "[subbed]"
        codec = (["-c:v", "copy"] if clip and video == "0:v" else
                 ["-c:v", "libx264", "-preset", "veryfast", "-crf", "20", "-pix_fmt", "yuv420p"])
        subprocess.run(["ffmpeg", "-v", "error", "-y", *source, *inputs,
                        *(["-filter_complex", ";".join(filters)] if filters else []),
                        "-map", video, "-map", "[a]" if paragraphs else "1:a", *codec, "-c:a", "aac",
                        "-ar", str(RATE), "-ac", "1", "-b:a", "128k", "-t", f"{duration:.9f}", str(output)],
                       check=True)
    return frames
