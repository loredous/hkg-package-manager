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

# manifest components, from the binary manifest
MANIFEST=$("$BUILD_TOOLS/aapt" dump xmltree "$APK" AndroidManifest.xml)
has() { echo "$MANIFEST" | grep -q "$1"; }
has 'A: android:name(0x01010003)="hkgpackagemanager.app.QueueService"' && ok "QueueService declared" || bad "QueueService declared"
has 'A: android:name(0x01010003)="hkgpackagemanager.app.MainActivity"' && ok "MainActivity declared" || bad "MainActivity declared"
has 'android.intent.action.MAIN' && has 'android.intent.category.LAUNCHER' && ok "MAIN/LAUNCHER" || bad "MAIN/LAUNCHER"
has 'hkgpackagemanager.app.ButtonReceiver' && ok "ButtonReceiver declared" || bad "ButtonReceiver declared"
# every string in core Button.allActions(); read from Button.java so the two cannot drift
ACTIONS=$(sed -n 's/.*"\([a-z]*\.[a-z.]*\.action\.[A-Z_]*\)".*/\1/p' core/src/hkgpackagemanager/core/Button.java | sort -u)
[ "$(echo "$ACTIONS" | wc -l)" = 2 ] && ok "2 button actions in Button.java" || bad "expected 2 button actions in Button.java"
for a in $ACTIONS; do
    has "\"$a\"" && ok "manifest has $a" || bad "manifest has $a"
done
has 'hkgpackagemanager.app.MountReceiver' && ok "MountReceiver declared" || bad "MountReceiver declared"
has 'android.intent.action.MEDIA_MOUNTED' && ok "MEDIA_MOUNTED" || bad "MEDIA_MOUNTED"
has 'android:scheme(0x01010027)="file"' && ok "scheme file" || bad "scheme file"

[ "$fail" = 0 ] && echo OK || { echo FAILED; exit 1; }
