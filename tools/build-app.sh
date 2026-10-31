#!/bin/sh
# Build the signed app APK: compile core/src and app/src against android.jar, dex them, package
# with the manifest and assets/resume.sh, zipalign and sign (v1 only).
# usage: tools/build-app.sh           writes build/app/hkgpm.apk and prints its path
# environment: VERSION_NAME (default 1.0.0), VERSION_CODE (default 1). They replace the values in
# app/AndroidManifest.xml (--replace-version), so keep the defaults and the manifest in step.
# The signing key is private/keys/hkgpm.jks (alias hkgpm), created on the first run with
# a random password in private/keys/password. Keep the key: an update must be signed with it.
set -e
ROOT=$(cd "$(dirname "$0")/.." && pwd)
eval "$("$ROOT/tools/toolchain.sh" env)"
cd "$ROOT"
VERSION_NAME=${VERSION_NAME:-1.0.0}
VERSION_CODE=${VERSION_CODE:-1}

OUT=build/app
rm -rf "$OUT"
mkdir -p "$OUT/classes" "$OUT/assets"

# org.json comes from android.jar (bootclasspath); the Maven json jar is never bundled
# shellcheck disable=SC2046 # one path per word; none contain spaces
javac -encoding UTF-8 -source 8 -target 8 -Xlint:-options -bootclasspath "$ANDROID_JAR" \
    -d "$OUT/classes" $(find core/src app/src -name '*.java' | sort)

# shellcheck disable=SC2046
"$BUILD_TOOLS/d8" --min-api 19 --lib "$ANDROID_JAR" --output "$OUT" \
    $(find "$OUT/classes" -name '*.class' | sort)

cp stick/resume.sh "$OUT/assets/resume.sh"
"$BUILD_TOOLS/aapt" package -f -M app/AndroidManifest.xml -I "$ANDROID_JAR" -A "$OUT/assets" \
    --version-name "$VERSION_NAME" --version-code "$VERSION_CODE" --replace-version -F "$OUT/unsigned.apk"
(cd "$OUT" && "$BUILD_TOOLS/aapt" add -k unsigned.apk classes.dex > /dev/null)
"$BUILD_TOOLS/zipalign" -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

# first run (both files missing): make the signing key, with a random password nobody has to see.
# The key can't be replaced, so one file without the other is an error and nothing is touched.
KEYS=private/keys
if [ -f "$KEYS/hkgpm.jks" ] && [ ! -f "$KEYS/password" ]; then
    echo "error: $KEYS/password is missing. The existing key $KEYS/hkgpm.jks was left alone." >&2
    echo "Restore the password file (the key can't be replaced; updates need it)." >&2
    exit 1
fi
if [ ! -f "$KEYS/hkgpm.jks" ] && [ -f "$KEYS/password" ]; then
    echo "error: $KEYS/hkgpm.jks is missing. The existing $KEYS/password was left alone." >&2
    echo "Restore the key file (the key can't be replaced; updates need it)." >&2
    exit 1
fi
if [ ! -f "$KEYS/hkgpm.jks" ]; then
    (umask 077 && mkdir -p "$KEYS" \
        && { LC_ALL=C tr -dc 'A-Za-z0-9' < /dev/urandom | dd bs=32 count=1 2> /dev/null; echo; } > "$KEYS/password")
    if ! keytool -genkeypair -keystore "$KEYS/hkgpm.jks" -storetype JKS -alias hkgpm \
        -keyalg RSA -keysize 2048 -validity 10950 -dname "CN=hkg-package-manager" \
        -storepass:file "$KEYS/password" -keypass:file "$KEYS/password" < /dev/null > "$OUT/keytool.log" 2>&1; then
        cat "$OUT/keytool.log" >&2
        echo "error: keytool failed; removing the half-made $KEYS/password" >&2
        rm -f "$KEYS/password" "$KEYS/hkgpm.jks"
        exit 1
    fi
    rm -f "$OUT/keytool.log"
fi
chmod 600 "$KEYS/hkgpm.jks" "$KEYS/password"

"$BUILD_TOOLS/apksigner" sign --ks "$KEYS/hkgpm.jks" --ks-key-alias hkgpm \
    --ks-pass "file:$KEYS/password" \
    --v1-signing-enabled true --v2-signing-enabled false --min-sdk-version 19 \
    --out "$OUT/hkgpm.apk" "$OUT/aligned.apk"
rm -f "$OUT/unsigned.apk" "$OUT/aligned.apk" "$OUT/hkgpm.apk.idsig"
echo "$OUT/hkgpm.apk"
