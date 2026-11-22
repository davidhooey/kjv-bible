#!/usr/bin/env python3
"""Pack the Kokoro chapter recordings into the per-book zips the app downloads.

For each voice and book this writes <out>/<voice>_<BB>.zip (BB = 01..66) holding CCC.mp3 for every
chapter plus timings.tsv (chapter<TAB>verse<TAB>start_ms, from tools/align_audio.py). MP3s are
already compressed, so entries are stored uncompressed. A chapter whose recording runs on well past
its last verse (align_audio.py's .ends.tsv) is trimmed.

    python tools/package_audio.py --kokoro C:/Users/David/src/kokoro \
        --timings .audio-work/timings --out .audio-work/zips
"""
import argparse
import glob
import hashlib
import os
import re
import shutil
import subprocess
import zipfile

VOICES = ["af_bella", "am_eric", "bm_daniel"]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--kokoro", required=True)
    ap.add_argument("--timings", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--voices", nargs="+", default=VOICES)
    ap.add_argument("--books", nargs="+", type=int)
    ap.add_argument("--ffmpeg", default=None)
    args = ap.parse_args()
    args.ffmpeg = args.ffmpeg or shutil.which("ffmpeg") or os.path.join(args.kokoro, "ffmpeg.exe")
    os.makedirs(args.out, exist_ok=True)

    total = 0
    for voice in args.voices:
        book_dirs = sorted(glob.glob(os.path.join(args.kokoro, voice, "[0-9][0-9]_*")))
        assert len(book_dirs) == 66, f"{voice}: expected 66 book folders, found {len(book_dirs)}"
        for bd in book_dirs:
            book = int(os.path.basename(bd)[:2])
            if args.books and book not in args.books:
                continue
            timings_path = os.path.join(args.timings, voice, f"{book:02d}.tsv")
            timing_chapters = {int(l.split("\t")[0]) for l in open(timings_path, encoding="utf-8") if l.strip()}
            mp3s = sorted(glob.glob(os.path.join(bd, "Chapter_*.mp3")))
            chapters = [int(re.search(r"(\d+)\.mp3$", m).group(1)) for m in mp3s]
            assert set(chapters) == timing_chapters == set(range(1, len(chapters) + 1)), \
                f"{voice} book {book}: chapters and timings don't match"
            out = os.path.join(args.out, f"{voice}_{book:02d}.zip")
            tmp = out + ".part"
            ends = {}
            ends_path = timings_path[:-4] + ".ends.tsv"
            if os.path.exists(ends_path):
                for l in open(ends_path, encoding="utf-8"):
                    c, text_end, audio_end = map(int, l.split())
                    ends[c] = (text_end, audio_end)
            with zipfile.ZipFile(tmp, "w", compression=zipfile.ZIP_STORED) as z:
                for ch, mp3 in zip(chapters, mp3s):
                    text_end, audio_end = ends.get(ch, (0, 0))
                    if audio_end - text_end > 2000:
                        # Something was read after the last verse (e.g. the Project Gutenberg
                        # licence after Revelation 22); cut it off.
                        cut = os.path.join(args.out, "trimmed.mp3")
                        subprocess.run([args.ffmpeg, "-v", "error", "-y", "-i", mp3, "-t", f"{text_end / 1000:.3f}",
                                        "-c", "copy", cut], check=True)
                        print(f"  {voice} {book:02d}:{ch:03d} trimmed {audio_end / 1000:.0f}s -> {text_end / 1000:.0f}s")
                        z.write(cut, f"{ch:03d}.mp3")
                        os.remove(cut)
                    else:
                        z.write(mp3, f"{ch:03d}.mp3")
                # timings.tsv goes last: the app treats it as the "book complete" marker.
                z.write(timings_path, "timings.tsv")
            os.replace(tmp, out)
            total += os.path.getsize(out)
        print(f"{voice}: done", flush=True)
    print(f"total {total / 1e9:.2f} GB")

    with open(os.path.join(args.out, "SHA256SUMS"), "w", newline="\n") as f:
        for path in sorted(glob.glob(os.path.join(args.out, "*.zip"))):
            h = hashlib.sha256()
            with open(path, "rb") as fh:
                for block in iter(lambda: fh.read(1 << 20), b""):
                    h.update(block)
            f.write(f"{h.hexdigest()}  {os.path.basename(path)}\n")


if __name__ == "__main__":
    main()
