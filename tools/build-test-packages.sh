#!/bin/sh
# Build the tiny on-car test packages (docs/testing/2026-10-30-phase-1.md) into
# dist/test-packages/: test.a.zip, test.b.zip, test.c.zip, test.fail.zip and "Test Spaces.zip".
# Each zip has NEMESIS.data at its root (built with ../hmg_nemesis/Sources/tools/make_nemesis_package.py).
# usage: tools/build-test-packages.sh [output dir]
set -e
ROOT=$(cd "$(dirname "$0")/.." && pwd)
OUT=${1:-$ROOT/dist/test-packages}
case "$OUT" in /*) ;; *) OUT=$(pwd)/$OUT ;; esac
MAKER=$ROOT/../hmg_nemesis/Sources/tools/make_nemesis_package.py
[ -f "$MAKER" ] || { echo "build-test-packages: missing $MAKER" >&2; exit 1; }
STAGE=$(mktemp -d)
trap 'rm -rf "$STAGE"' EXIT

# mk <dir name> <id> <name> <version> [preinstall|postinstall]  (script body on stdin)
mk() {
    d=$STAGE/$1
    mkdir -p "$d/scripts"
    {
        echo "[nemesis]"
        echo "id=$2"
        echo "name=$3"
        echo "version=$4"
        echo "$5=scripts/$5.sh"
    } > "$d/NEMESIS.data"
    { echo "#!/system/bin/sh"; cat; } > "$d/scripts/$5.sh"
    chmod 755 "$d/scripts/$5.sh"
}
marker() { echo "echo \"$1\" > /data/nemesis/$1.marker"; echo "exit 0"; }

marker test.a | mk test.a test.a "Test A" 1.0.0 postinstall
{ echo 'echo "test.b: restarting"'; echo 'exec /system/bin/reboot'; } | mk test.b test.b "Test B" 1.0.0 postinstall
marker test.c | mk test.c test.c "Test C" 1.0.0 postinstall
{ echo 'echo "test.fail: refusing on purpose"'; echo 'exit 1'; } | mk test.fail test.fail "Test Fail" 1.0.0 preinstall
marker test.spaces | mk spaces test.spaces "Test Spaces" 1.0.0 postinstall

"$ROOT/tools/lint-sh.sh" "$STAGE"
mkdir -p "$OUT"
rm -f "$OUT"/*.zip
for p in test.a test.b test.c test.fail; do
    python3 "$MAKER" "$STAGE/$p" -o "$OUT/$p.zip" > /dev/null
done
python3 "$MAKER" "$STAGE/spaces" -o "$OUT/Test Spaces.zip" > /dev/null
ls "$OUT"
