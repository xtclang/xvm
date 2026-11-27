"""Exercise tour narration offline with a deterministic speech engine."""

import json
import shutil
import subprocess
import tempfile
import unittest
import wave
from pathlib import Path

from PIL import Image

import compose
import narration


class NarrationTest(unittest.TestCase):
    def setUp(self):
        temp = tempfile.TemporaryDirectory()
        self.addCleanup(temp.cleanup)
        self.root = Path(temp.name)
        self.calls = []
        self.speech = narration.SpeechCache(self.root / "cache", "fake", self.synthesize)

    def synthesize(self, text, output):
        self.calls.append(text)
        with wave.open(str(output), "wb") as wav:
            wav.setparams((1, 2, narration.RATE, 0, "NONE", "not compressed"))
            wav.writeframes(bytes(2 * narration.RATE))

    def test_parse_and_match_after_renumbering(self):
        script = self.root / "narration.md"
        script.write_text("""# Tour
Preamble.
## A
First section.
<!-- Hidden
### Also hidden -->
### Card
The opening
card.
### Gone
Uncaptured step.
## B
Second section.
### Card
Different step.
## Omitted
Never shown.
""")
        steps = [dict(section="A", title="Card", number=4, card={}),
                 dict(section="B", title="New", number=1),
                 dict(section="B", title="Card", number=2)]
        spoken, warnings = narration.match(narration.read_script(script), steps)
        self.assertEqual(spoken, [["First section.", "The opening card."], [],
                                  ["Second section.", "Different step."]])
        self.assertEqual(warnings, ["No narration for step: B / New", "Unmatched narration: A / Gone",
                                    "Unmatched narration: Omitted"])

    def test_duplicate_titles_within_one_section(self):
        script = self.root / "bad.md"
        script.write_text("## One\nIntro\n### Title\nText\n### Title\nOther\n")
        with self.assertRaisesRegex(ValueError, "duplicate heading"):
            narration.read_script(script)

    def test_cache_separates_text_and_voice(self):
        first = self.speech.paragraph("Hello.")
        self.assertEqual(self.speech.paragraph("Hello."), first)
        self.assertNotEqual(self.speech.paragraph("Hello again.").audio, first.audio)
        other = narration.SpeechCache(self.speech.directory, "other", self.synthesize)
        self.assertNotEqual(other.paragraph("Hello.").audio, first.audio)
        self.assertEqual(self.calls, ["Hello.", "Hello again.", "Hello."])

    def test_concurrent_cache_miss_keeps_the_first_completed_take(self):
        first = self.speech
        def overlapping_synthesis(text, output):
            self.synthesize(text, output)
            first.paragraph(text)
            with wave.open(str(output), "wb") as wav:
                wav.setparams((1, 2, narration.RATE, 0, "NONE", "not compressed"))
                wav.writeframes(bytes(4 * narration.RATE))
        second = narration.SpeechCache(first.directory, first.identity, overlapping_synthesis)
        self.assertEqual(second.paragraph("Same text").seconds, 1)
        self.assertEqual(first.paragraph("Same text").seconds, 1)

    def test_pronunciation_in_prose_commands_and_tasks(self):
        text = "The XTC plugin's xtc init, compileXtc, testXtc and runXtc. Open the XDK."
        para = self.speech.paragraph(text)
        self.assertEqual(self.calls, ["The Ecstasy plugin's Ecstasy init, compile Ecstasy, test Ecstasy and run Ecstasy. Open the XDK."])
        self.assertEqual(para.text, text)
        self.assertEqual(self.speech.paragraph(self.calls[0]).audio, para.audio)

    def test_segment_duration_covers_the_picture_speech_and_pauses(self):
        para = self.speech.paragraph("Words")
        self.assertEqual(narration.segment_frames(2, [], 7), 30)
        self.assertEqual(narration.segment_frames(2, [para], 0.6), 30)
        self.assertEqual(narration.segment_frames(0.5, [para], 0.6), 24)
        self.assertEqual(narration.segment_frames(0.5, [para, para], 0.6), 54)

    def test_subtitle_wrapping_clock_and_paragraph_gap(self):
        first = self.speech.paragraph("Read this sentence aloud. " + "The editor follows your cursor " * 8 + "here.")
        second = self.speech.paragraph("A < B & C.")
        cues = narration.paragraph_cues([first, second], 3599.5)
        self.assertAlmostEqual(cues[0].start, 3599.5)
        self.assertAlmostEqual(cues[-2].end, 3600.5)
        self.assertAlmostEqual(cues[-1].start, 3601.5)
        self.assertAlmostEqual(cues[-1].end, 3602.5)
        for cue in cues:
            self.assertLess(cue.start, cue.end)
            self.assertLessEqual(len(cue.text.splitlines()), 2)
            self.assertTrue(all(len(line) <= 42 for line in cue.text.splitlines()))
        self.assertEqual(" ".join(" ".join(c.text.split()) for c in cues), first.text + " " + second.text)
        movie = self.root / "tour.mp4"
        narration.write_subtitles(movie, cues)
        self.assertIn("00:59:59,500 -->", movie.with_suffix(".srt").read_text())
        vtt = movie.with_suffix(".vtt").read_text()
        self.assertTrue(vtt.startswith("WEBVTT\n\n"))
        self.assertIn("01:00:02.500\nA &lt; B &amp; C.", vtt)

    def test_subtitles_leave_the_measured_sentence_pause_clear(self):
        audio = self.root / "phrasing.wav"
        with wave.open(str(audio), "wb") as wav:
            wav.setparams((1, 2, narration.RATE, 0, "NONE", "not compressed"))
            for seconds, value in ((0.7, 1000), (0.75, 0), (0.55, 1000)):
                wav.writeframes(value.to_bytes(2, "little") * round(seconds * narration.RATE))
        para = narration.Paragraph("First sentence. Second sentence.", audio, 2 * narration.RATE)
        cues = narration.paragraph_cues([para])
        self.assertAlmostEqual(cues[0].end, 0.7)
        self.assertAlmostEqual(cues[1].start, 1.45)

    def test_slide_notes(self):
        step = {"caption": "Short caption"}
        self.assertEqual(compose.slide_notes(step), step["caption"])
        step["paragraphs"] = ["Intro", "Step"]
        self.assertEqual(compose.slide_notes(step), "Intro\n\nStep")

    @unittest.skipUnless(shutil.which("ffmpeg") and shutil.which("ffprobe"), "requires ffmpeg and ffprobe")
    def test_still_timeline_matches_every_original_frame(self):
        frames = []
        for i, color in enumerate(("red", "green")):
            path = self.root / f"step-{i}.png"
            Image.new("RGB", (16, 16), color).save(path)
            frames.append(path)
        steps = [{"caption": "First."}, {"caption": "This step has a title card.", "card": True}]
        movie = self.root / "original.mp4"
        compose.video(frames, steps, self.root, movie)
        rgb = subprocess.check_output(["ffmpeg", "-v", "error", "-i", str(movie), "-vf", "scale=1:1",
                                       "-pix_fmt", "rgb24", "-f", "rawvideo", "-"])
        red = sum(r > g for r, g in zip(rgb[::3], rgb[1::3]))
        self.assertEqual(compose.still_frame_counts(steps), [red, len(rgb) // 3 - red])

    @unittest.skipUnless(shutil.which("ffmpeg") and shutil.which("ffprobe"), "requires ffmpeg and ffprobe")
    def test_recorded_and_frame_only_short_long_and_silent(self):
        still = self.root / "frame.png"
        Image.new("RGB", (480, 200), "#303030").save(still)
        clip = self.root / "original.mp4"
        subprocess.run(["ffmpeg", "-v", "error", "-loop", "1", "-framerate", "15", "-i", str(still),
                        "-t", "2", "-pix_fmt", "yuv420p", str(clip)], check=True)
        para = self.speech.paragraph("A sentence.")
        for source in (None, clip):
            for spoken in ([], [para], [para, para]):
                with self.subTest(recorded=bool(source), paragraphs=len(spoken)):
                    movie = self.root / "segment.mp4"
                    frames = narration.render_segment(still, source, 2, spoken, 0.6, movie,
                                                      (480, 200, compose.font(18)))
                    streams = json.loads(subprocess.check_output(
                        ["ffprobe", "-v", "error", "-show_streams", "-of", "json", str(movie)]))["streams"]
                    video, audio = streams
                    self.assertEqual(int(video["nb_frames"]), frames)
                    self.assertEqual((audio["codec_name"], audio["sample_rate"], audio["channels"]), ("aac", "48000", 1))
                    self.assertAlmostEqual(float(audio["duration"]), frames / narration.FPS, places=3)


if __name__ == "__main__":
    unittest.main()
