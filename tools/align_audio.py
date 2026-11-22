#!/usr/bin/env python3
"""Find where every verse starts in the Kokoro chapter recordings.

The recordings were made one chapter at a time from a text in which verses are often merged into
one paragraph, so the pauses in the audio don't mark verses. Instead this force-aligns each
chapter's audio against the text that was spoken (wav2vec2 MMS_FA, on the GPU if there is one),
then maps the app's verses (assets/kjv.tsv) onto the aligned words.

On Windows, run it inside WSL (Ubuntu) with torch/torchaudio 2.7.1 (cu128), numpy and ffmpeg
installed: Windows Application Control may refuse to load PyTorch's CUDA DLLs natively.

Output, per voice and book: <out>/<voice>/<BB>.tsv with lines  chapter<TAB>verse<TAB>start_ms,
and <BB>.ends.tsv with  chapter<TAB>text_end_ms<TAB>audio_ms  (used to trim trailing extras)
Existing outputs are skipped, so the script can be stopped and rerun.

    python tools/align_audio.py --kokoro C:/Users/David/src/kokoro --out .audio-work/timings
"""
import argparse
import difflib
import glob
import os
import re
import shutil
import subprocess
import sys
import time

import numpy as np
import torch
import torchaudio

SR = 16000
VOICES = ["af_bella", "am_eric", "bm_daniel"]

ONES = ("zero one two three four five six seven eight nine ten eleven twelve thirteen fourteen "
        "fifteen sixteen seventeen eighteen nineteen").split()
TENS = "_ _ twenty thirty forty fifty sixty seventy eighty ninety".split()


def number_words(n):
    if n < 20:
        return ONES[n]
    if n < 100:
        return TENS[n // 10] + ("" if n % 10 == 0 else " " + ONES[n % 10])
    if n < 1000:
        rest = n % 100
        return ONES[n // 100] + " hundred" + ("" if rest == 0 else " " + number_words(rest))
    if n < 1_000_000:
        rest = n % 1000
        return number_words(n // 1000) + " thousand" + ("" if rest == 0 else " " + number_words(rest))
    return " ".join(ONES[int(d)] for d in str(n))  # long numbers: read digit by digit


def words(text):
    """Lower-case words made only of the aligner's characters (a-z and apostrophe)."""
    text = text.replace("\u2019", "'").replace("[", "").replace("]", "")
    text = re.sub(r"\d+", lambda m: " " + number_words(int(m.group())) + " ", text)
    text = re.sub(r"[^a-z']+", " ", text.lower())
    return [w.strip("'") for w in text.split() if w.strip("'")]


def load_verses(tsv_path):
    verses = {}
    with open(tsv_path, encoding="utf-8") as f:
        for line in f:
            b, c, v, t = line.rstrip("\n").split("\t", 3)
            verses.setdefault((int(b), int(c)), []).append((int(v), t))
    return verses


def decode(ffmpeg, path):
    raw = subprocess.run([ffmpeg, "-v", "error", "-i", path, "-f", "f32le", "-ac", "1", "-ar", str(SR), "-"],
                         check=True, capture_output=True).stdout
    return np.frombuffer(raw, dtype=np.float32).copy()


class Aligner:
    def __init__(self, device):
        bundle = torchaudio.pipelines.MMS_FA
        self.device = device
        self.model = bundle.get_model().to(device).eval()
        self.tokenizer = bundle.get_tokenizer()
        self.aligner = bundle.get_aligner()

    @torch.inference_mode()
    def emission(self, audio, chunk_s=30, ctx_s=1):
        # Run the model on 30 s windows with 1 s of context either side and keep the middle frames,
        # so memory stays flat however long the chapter is. 320 samples per output frame.
        hop, chunk, ctx = 320, chunk_s * SR, ctx_s * SR
        total_frames = len(audio) // hop
        out = []
        for s in range(0, len(audio), chunk):
            a, b = max(0, s - ctx), min(len(audio), s + chunk + ctx)
            x = torch.from_numpy(audio[a:b]).to(self.device).unsqueeze(0)
            em, _ = self.model(x)
            first = (s - a) // hop
            want = min(chunk, len(audio) - s) // hop
            out.append(em[0, first:first + want].float().cpu())
        em = torch.cat(out)
        return em[:total_frames], len(audio) / max(1, em.shape[0])

    def align(self, audio, spoken):
        em, samples_per_frame = self.emission(audio)
        spans = self.aligner(em, self.tokenizer(spoken))
        to_s = samples_per_frame / SR
        starts = [s[0].start * to_s for s in spans]
        ends = [s[-1].end * to_s for s in spans]
        scores = [sum(t.score * (t.end - t.start) for t in s) / max(1, sum(t.end - t.start for t in s)) for s in spans]
        return starts, ends, scores


def verse_starts(verses, spoken, starts, ends):
    """Map each verse's first word onto the spoken words and return start times in ms."""
    app_words, owner = [], []
    for v, text in verses:
        for w in words(text):
            app_words.append(w)
            owner.append(v)
    match = {}
    sm = difflib.SequenceMatcher(None, app_words, spoken, autojunk=False)
    for blk in sm.get_matching_blocks():
        for k in range(blk.size):
            match[blk.a + k] = blk.b + k
    result, missed_first = [], 0
    i = 0
    for v, _ in verses:
        first = i
        while i < len(owner) and owner[i] == v:
            i += 1
        idx = [k for k in range(first, i) if k in match]
        if not idx:
            result.append((v, None))
            continue
        if idx[0] != first:
            missed_first += 1
        j = match[idx[0]] - (idx[0] - first)  # step back over unmatched leading words
        j = max(0, min(j, len(spoken) - 1))
        t = starts[j]
        prev_end = ends[j - 1] if j > 0 else 0.0
        lead = min(0.3, max(0.0, (t - prev_end) * 0.5))  # start a little before the first word
        result.append((v, int(round((t - lead) * 1000))))
    # Fill any verse that had no matched words by interpolating between its neighbours.
    for n, (v, ms) in enumerate(result):
        if ms is None:
            before = next((m for _, m in reversed(result[:n]) if m is not None), 0)
            after = next((m for _, m in result[n + 1:] if m is not None), before)
            result[n] = (v, (before + after) // 2)
    matched = len(match) / max(1, len(app_words))
    # Where the chapter's text ends in the audio, so trailing extras (a section heading or the
    # Project Gutenberg licence read aloud after the last verse) can be trimmed when packaging.
    end_ms = int(round(ends[max(match.values())] * 1000)) + 600 if match else None
    return result, matched, missed_first, end_ms


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--kokoro", required=True, help="folder with the voice folders and chapters_split")
    ap.add_argument("--tsv", default=os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "kjv.tsv"))
    ap.add_argument("--out", required=True)
    ap.add_argument("--voices", nargs="+", default=VOICES)
    ap.add_argument("--books", nargs="+", type=int, help="only these book numbers (1-66)")
    ap.add_argument("--ffmpeg", default=None)
    ap.add_argument("--reverse", action="store_true",
                    help="work from the last voice and book backwards, so a second copy can run alongside")
    args = ap.parse_args()

    ffmpeg = args.ffmpeg or shutil.which("ffmpeg") or os.path.join(args.kokoro, "ffmpeg.exe")
    device = "cuda" if torch.cuda.is_available() else "cpu"
    print(f"device: {device}", flush=True)
    aligner = Aligner(device)
    verses = load_verses(args.tsv)
    text_root = os.path.join(args.kokoro, "chapters_split")
    book_dirs = sorted(os.path.basename(d) for d in glob.glob(os.path.join(text_root, "[0-9][0-9]_*")))
    assert len(book_dirs) == 66, f"expected 66 book folders, found {len(book_dirs)}"

    os.makedirs(args.out, exist_ok=True)
    log = open(os.path.join(args.out, "align.log"), "a", encoding="utf-8")
    t0, done_audio = time.time(), 0.0
    order = (lambda xs: list(reversed(xs))) if args.reverse else list
    for voice in order(args.voices):
        os.makedirs(os.path.join(args.out, voice), exist_ok=True)
        for bd in order(book_dirs):
            book = int(bd[:2])
            if args.books and book not in args.books:
                continue
            out_path = os.path.join(args.out, voice, f"{book:02d}.tsv")
            if os.path.exists(out_path):
                continue
            lines, end_lines = [], []
            chapters = sorted(glob.glob(os.path.join(text_root, bd, "Chapter_*.txt")))
            for txt in chapters:
                ch = int(re.search(r"(\d+)\.txt$", txt).group(1))
                mp3 = os.path.join(args.kokoro, voice, bd, f"Chapter_{ch:03d}.mp3")
                spoken = words(open(txt, encoding="utf-8").read())
                audio = decode(ffmpeg, mp3)
                starts, ends, scores = aligner.align(audio, spoken)
                result, matched, missed, end_ms = verse_starts(verses[(book, ch)], spoken, starts, ends)
                done_audio += len(audio) / SR
                audio_ms = int(len(audio) * 1000 / SR)
                end_lines.append(f"{ch}\t{min(end_ms or audio_ms, audio_ms)}\t{audio_ms}")
                bad = [v for (v, a), (_, b) in zip(result, result[1:]) if b <= a]
                note = f"{voice} {book:02d}:{ch:03d} matched={matched:.3f} missed_first={missed} " \
                       f"score={np.mean(scores):.2f} nonmonotonic={bad}"
                if matched < 0.97 or missed or bad:
                    print("  CHECK " + note, flush=True)
                log.write(note + "\n")
                lines += [f"{ch}\t{v}\t{ms}" for v, ms in result]
            # <BB>.ends.tsv: chapter<TAB>text_end_ms<TAB>audio_ms. Written first, because <BB>.tsv
            # existing is what marks the book as done.
            with open(out_path[:-4] + ".ends.tsv", "w", encoding="utf-8", newline="\n") as f:
                f.write("\n".join(end_lines) + "\n")
            with open(out_path + ".part", "w", encoding="utf-8", newline="\n") as f:
                f.write("\n".join(lines) + "\n")
            os.replace(out_path + ".part", out_path)
            log.flush()
            el = time.time() - t0
            print(f"{voice} book {book:02d} done ({len(chapters)} ch); {done_audio / 3600:.1f} h audio in "
                  f"{el / 60:.1f} min ({done_audio / max(el, 1):.0f}x realtime)", flush=True)


if __name__ == "__main__":
    sys.exit(main())
