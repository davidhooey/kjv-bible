# kjv-bible

An offline King James Version Bible reader for Android, written in Kotlin with Jetpack Compose.

## Features

- Swipe between chapters across the whole Bible, or use Previous / Next at the end of a chapter
- Book → chapter → verse picker
- Search by words (all must match), or type a reference such as `john 3:16`, `1 cor 13`, `ps 23`
- Tap verses to bookmark, copy or share them
- Translator-supplied words shown in italics, as in printed KJVs
- Adjustable text size; System, Light, Sepia and Dark themes
- Remembers your place; works fully offline and requests no permissions

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

## Installing

Download `kjv-bible-<version>.apk` from the
[Releases](https://github.com/squished18/kjv-bible/releases) page and open it on your phone
(allow installs from that app when Android asks). Each release lists the APK's SHA-256 checksum
and the signing certificate's fingerprint so you can check the download.

`tools/setup-vm.sh` sets up a fresh Ubuntu 24.04 machine (JDK, Android command-line tools, Gradle).

## Bible text

`app/src/main/assets/kjv.tsv` holds the 66-book KJV protocanon (31,102 verses), one verse per line:
`book<TAB>chapter<TAB>verse<TAB>text`, with translator-supplied words in `[brackets]`.

It was generated from the public-domain `eng-kjv2006` VPL edition, courtesy of
[eBible.org](https://ebible.org/find/details.php?id=eng-kjv2006) and the Crosswire Bible Society:

```sh
python3 tools/vpl_to_tsv.py eng-kjv2006_vpl.txt app/src/main/assets/kjv.tsv
```

## License

The app's code is licensed under the GNU GPL v2; see [LICENSE](LICENSE). The KJV text is in the
public domain (outside the UK, where Crown letters patent apply to printed copies).
