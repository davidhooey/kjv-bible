# kjv-bible

A King James Version Bible reader for Android, written in Kotlin with Jetpack Compose.

## Features

- Swipe between chapters across the whole Bible, or use Previous / Next at the end of a chapter
- Book → chapter → verse picker
- Search by words (all must match), or type a reference such as `john 3:16`, `1 cor 13`, `ps 23`
- Tap verses to bookmark, copy, share, or **listen** from that verse on
- Audio in three voices (Bella, Eric, Daniel); the verse being read is highlighted and followed,
  and playback carries on in the background with lock-screen controls
- Translator-supplied words shown in italics, as in printed KJVs
- Adjustable text size; System, Light, Sepia and Dark themes
- Remembers your place. The text works fully offline; audio downloads one book at a time the first
  time you play it, and is kept for offline listening (Settings shows the space used and can
  delete it)

## Building

Requires JDK 17 and the Android SDK (platform 35). With `sdk.dir` set in `local.properties`
or `ANDROID_HOME` set:

```sh
./gradlew assembleRelease      # app/build/outputs/apk/release/app-release.apk
./gradlew testDebugUnitTest    # unit tests against the bundled text
```

Release builds are signed with a key kept outside the repo. Run `tools/create-release-key.sh`
once to create it; it records the key's location and password in `~/.gradle/gradle.properties`.
Without that, `assembleRelease` produces an unsigned APK.

Debug builds install beside the release app (as `io.github.squished18.kjvbible.debug`) and fetch
audio from `http://127.0.0.1:8765` instead of GitHub, so audio can be tested before it is
published: serve a folder of book zips there and run `adb reverse tcp:8765 tcp:8765`.

`tools/setup-vm.sh` sets up a fresh Ubuntu 24.04 machine (JDK, Android command-line tools, Gradle).

## Installing

Download `kjv-bible-<version>.apk` from the
[Releases](https://github.com/squished18/kjv-bible/releases) page and open it on your phone
(allow installs from that app when Android asks). Each release lists the APK's SHA-256 checksum
and the signing certificate's fingerprint so you can check the download.

## Bible text

`app/src/main/assets/kjv.tsv` holds the 66-book KJV protocanon (31,102 verses), one verse per line:
`book<TAB>chapter<TAB>verse<TAB>text`, with translator-supplied words in `[brackets]`.

It was generated from the public-domain `eng-kjv2006` VPL edition, courtesy of
[eBible.org](https://ebible.org/find/details.php?id=eng-kjv2006) and the Crosswire Bible Society:

```sh
python3 tools/vpl_to_tsv.py eng-kjv2006_vpl.txt app/src/main/assets/kjv.tsv
```

## Audio

The recordings were generated chapter by chapter with the
[Kokoro-82M](https://huggingface.co/hexgrad/Kokoro-82M) text-to-speech model (Apache 2.0) in the
voices `af_bella`, `am_eric` and `bm_daniel`. They are published as one zip per voice and book
(`<voice>_<BB>.zip`, BB = 01–66) on the `audio-v1` release, separate from app releases. Each zip
holds `CCC.mp3` per chapter and `timings.tsv` (`chapter<TAB>verse<TAB>start_ms`).

The verse timings come from forced alignment of each recording against its text:

```sh
# Needs torch/torchaudio 2.7.1 with CUDA, numpy and ffmpeg (on Windows, run this inside WSL).
python tools/align_audio.py --kokoro <kokoro folder> --out .audio-work/timings
python tools/package_audio.py --kokoro <kokoro folder> --timings .audio-work/timings --out .audio-work/zips
```

`package_audio.py` also trims anything read after a chapter's last verse (the source text had a
section heading after Malachi 4 and the Project Gutenberg licence after Revelation 22).

## License

The app's code is licensed under the GNU GPL v2; see [LICENSE](LICENSE). The KJV text is in the
public domain (outside the UK, where Crown letters patent apply to printed copies).
