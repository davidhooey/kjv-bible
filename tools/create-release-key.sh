#!/usr/bin/env bash
# Creates the release signing key (run once, interactively) and records its location and
# password in ~/.gradle/gradle.properties so `./gradlew assembleRelease` can sign builds.
#
# BACK UP the keystore file and its password afterwards. Phones only accept an update that is
# signed with the same key as the installed app.
set -euo pipefail

KEYDIR="$HOME/keys"
KS="$KEYDIR/kjvbible-release.p12"
ALIAS=kjvbible

if [ -e "$KS" ]; then
  echo "$KS already exists; refusing to overwrite it." >&2
  exit 1
fi

read -rp "Name to put on the signing certificate (e.g. your name): " CN
while true; do
  read -rsp "Choose a keystore password (at least 8 characters): " P1; echo
  read -rsp "Repeat the password: " P2; echo
  if [ "$P1" = "$P2" ] && [ ${#P1} -ge 8 ]; then break; fi
  echo "The passwords didn't match or were too short; try again."
done

mkdir -p "$KEYDIR" && chmod 700 "$KEYDIR"
export KJV_KS_PASS="$P1"
keytool -genkeypair -keystore "$KS" -storetype PKCS12 -alias "$ALIAS" \
  -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=${CN//,/\\,}" \
  -storepass:env KJV_KS_PASS -keypass:env KJV_KS_PASS
chmod 600 "$KS"

mkdir -p "$HOME/.gradle"
PROPS="$HOME/.gradle/gradle.properties"
touch "$PROPS" && chmod 600 "$PROPS"
sed -i '/^kjvbible\./d' "$PROPS"
{
  printf 'kjvbible.keystore=%s\n' "$KS"
  printf 'kjvbible.keyAlias=%s\n' "$ALIAS"
  printf 'kjvbible.storePassword=%s\n' "${P1//\\/\\\\}"   # backslashes must be escaped
} >> "$PROPS"

echo
echo "Created $KS"
keytool -list -v -keystore "$KS" -storetype PKCS12 -storepass:env KJV_KS_PASS | grep -E "SHA256:" | head -1
unset KJV_KS_PASS P1 P2
echo
echo "Now back up $KS and its password (for example in a password manager)."
