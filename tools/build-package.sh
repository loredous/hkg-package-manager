#!/bin/sh
# Build the HKG Package Manager NEMESIS install zip: an outer NEMESIS.data (type=install) and
# packages/hkg.package.manager.zip, which holds NEMESIS.data, scripts/, app/hkgpm.apk and
# BUILD ("<git describe> <UTC time>").
# usage: tools/build-package.sh [output zip]   (default: dist/hkg-package-manager.zip)
# Needs build/app/hkgpm.apk (tools/build-app.sh).
set -e
ROOT=$(cd "$(dirname "$0")/.." && pwd)
OUT=${1:-$ROOT/dist/hkg-package-manager.zip}
case "$OUT" in /*) ;; *) OUT=$(pwd)/$OUT ;; esac
ID=hkg.package.manager
APK=$ROOT/build/app/hkgpm.apk
[ -s "$APK" ] || { echo "build-package: missing $APK (run tools/build-app.sh)" >&2; exit 1; }

"$ROOT/tools/lint-sh.sh" "$ROOT/package" "$ROOT/stick"

STAGE=$(mktemp -d)
trap 'rm -rf "$STAGE"' EXIT
I=$STAGE/inner
mkdir -p "$I/scripts" "$I/app" "$STAGE/outer/packages"
cp "$ROOT/package/NEMESIS.data" "$I/"
cp "$ROOT"/package/scripts/*.sh "$I/scripts/"
cp "$APK" "$I/app/hkgpm.apk"
echo "$(git -C "$ROOT" describe --always --dirty 2>/dev/null || echo unknown) $(date -u +%Y-%m-%dT%H:%MZ)" > "$I/BUILD"
# fixed modes and timestamps, so only the sources and BUILD change the zip
find "$I" -type d -exec chmod 755 {} +
find "$I" -type f -exec chmod 644 {} +
chmod 755 "$I"/scripts/*.sh
find "$I" -exec touch -t 202601010000 {} +
(cd "$I" && zip -qrX "$STAGE/outer/packages/$ID.zip" NEMESIS.data BUILD scripts app)
{
    echo "[nemesis]"
    echo "version=1.0"
    echo "type=install"
    echo "path=packages/$ID.zip"
} > "$STAGE/outer/NEMESIS.data"
chmod 644 "$STAGE/outer/NEMESIS.data" "$STAGE/outer/packages/$ID.zip"
find "$STAGE/outer" -exec touch -t 202601010000 {} +
mkdir -p "$(dirname "$OUT")" && rm -f "$OUT"
(cd "$STAGE/outer" && zip -qrX "$OUT" NEMESIS.data packages)
echo "$OUT"
