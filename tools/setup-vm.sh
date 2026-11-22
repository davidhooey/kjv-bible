#!/usr/bin/env bash
# One-time setup of an Ubuntu 24.04 build VM: JDK 17, Android SDK command-line tools, Gradle.
# Safe to re-run; skips steps that are already done.
set -euo pipefail

SDK="$HOME/android-sdk"
GRADLE_VERSION=8.11.1
CMDLINE_ZIP=commandlinetools-linux-13114758_latest.zip

sudo apt-get update -qq
sudo DEBIAN_FRONTEND=noninteractive apt-get install -y -qq openjdk-17-jdk-headless unzip wget git python3 >/dev/null

if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  mkdir -p "$SDK/cmdline-tools" /tmp/cmdline
  wget -q "https://dl.google.com/android/repository/$CMDLINE_ZIP" -O /tmp/cmdline/tools.zip
  unzip -q -o /tmp/cmdline/tools.zip -d /tmp/cmdline
  rm -rf "$SDK/cmdline-tools/latest"
  mv /tmp/cmdline/cmdline-tools "$SDK/cmdline-tools/latest"
  rm -rf /tmp/cmdline
fi

export ANDROID_HOME="$SDK"
yes | "$SDK/cmdline-tools/latest/bin/sdkmanager" --licenses >/dev/null || true
"$SDK/cmdline-tools/latest/bin/sdkmanager" --install "platform-tools" "platforms;android-35" "build-tools;35.0.0" >/dev/null

if [ ! -x "$HOME/gradle/gradle-$GRADLE_VERSION/bin/gradle" ]; then
  mkdir -p "$HOME/gradle"
  wget -q "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -O /tmp/gradle.zip
  unzip -q -o /tmp/gradle.zip -d "$HOME/gradle"
  rm /tmp/gradle.zip
fi

# Environment for interactive shells.
if ! grep -q ANDROID_HOME "$HOME/.bashrc"; then
  cat >> "$HOME/.bashrc" <<EOF
export ANDROID_HOME=$SDK
export PATH=\$PATH:$SDK/platform-tools:$SDK/cmdline-tools/latest/bin:$HOME/gradle/gradle-$GRADLE_VERSION/bin
EOF
fi

java -version 2>&1 | head -1
"$SDK/platform-tools/adb" version | head -1
"$HOME/gradle/gradle-$GRADLE_VERSION/bin/gradle" --version | grep '^Gradle'
echo "SETUP_DONE"
