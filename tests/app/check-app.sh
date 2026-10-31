#!/bin/sh
# Build the APK and check its manifest, contents and signature.
# usage: sh tests/app/check-app.sh   (from the repo root)
set -e
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
eval "$("$ROOT/tools/toolchain.sh" env)"
cd "$ROOT"

sh tools/build-app.sh > /dev/null
APK=build/app/hkgpm.apk
fail=0
ok() { echo "ok   $1"; }
bad() { echo "FAIL $1"; fail=1; }

BADGING=$("$BUILD_TOOLS/aapt" dump badging "$APK")
echo "$BADGING" | sed -n 1p | grep -q "package: name='hkgpackagemanager.app'" && ok "package name" || bad "package name"
echo "$BADGING" | grep -q "^sdkVersion:'19'" && ok "sdkVersion 19" || bad "sdkVersion 19"
unzip -l "$APK" | grep -q ' classes.dex$' && ok "classes.dex" || bad "classes.dex"
unzip -l "$APK" | grep -q ' assets/resume.sh$' && ok "assets/resume.sh" || bad "assets/resume.sh"
unzip -p "$APK" assets/resume.sh | cmp -s - stick/resume.sh && ok "resume.sh identical to stick/resume.sh" || bad "resume.sh differs"
"$BUILD_TOOLS/apksigner" verify "$APK" > /dev/null 2>&1 && ok "apksigner verify" || bad "apksigner verify"

[ "$fail" = 0 ] && echo OK || { echo FAILED; exit 1; }
