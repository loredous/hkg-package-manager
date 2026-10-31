#!/bin/sh
# Host test for the NEMESIS package: runs its hooks under $MOCK_SH against a fake root
# (PM_SYSROOT) with stubbed nemesis, am and getprop, then checks the built zip's layout.
# usage: sh tests/package/run.sh [package zip]   (default: dist/hkg-package-manager.zip)
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
STUBS=$ROOT/tests/package/stubs
ZIP=${1:-$ROOT/dist/hkg-package-manager.zip}
eval "$("$ROOT/tools/toolchain.sh" env)"
: "${MOCK_SH:=sh}"
FAILS=0

check() {
    _name=$1; shift
    if "$@"; then echo "ok   $_name"; else echo "FAIL $_name"; FAILS=$((FAILS + 1)); fi
}
fails() { ! "$@"; }
has() { case "$(cat "$1" 2>/dev/null)" in *"$2"*) return 0 ;; esac; return 1; }

T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT

# fresh_root: a fake root with the nemesis stub as /system/xbin/nemesis
fresh_root() {
    rm -rf "$T/root" "$T/stub.log"
    mkdir -p "$T/root/system/xbin" "$T/root/data/nemesis"
    cp "$STUBS/nemesis" "$T/root/system/xbin/nemesis"
    chmod 755 "$T/root/system/xbin/nemesis"
    : > "$T/stub.log"
}
# run_hook <script>: sets RC; hooks run from a scratch cwd like NEMESIS does
run_hook() {
    RC=0
    (cd "$T" && PM_SYSROOT=$T/root PM_TEST_PATH=$STUBS PM_STUB_LOG=$T/stub.log \
        $MOCK_SH "$ROOT/package/scripts/$1" > "$T/out.txt" 2>&1) || RC=$?
}
STATE=$T/root/data/nemesis/hkg.package.manager

fresh_root; PM_NEMESIS_FAIL=1; export PM_NEMESIS_FAIL
run_hook pre.sh
check "pre: channel down exits 1"       test "$RC" = 1
check "pre: channel down toasts"        has "$T/stub.log" "HKG Package Manager needs NEMESIS with its command channel running."
check "pre: checked uid 10999"          has "$T/stub.log" "nemesis root 10999 -c /system/xbin/nemesis pkg-list"
unset PM_NEMESIS_FAIL

fresh_root
run_hook pre.sh
check "pre: channel up exits 0"         test "$RC" = 0
check "pre: no toast"                   fails has "$T/stub.log" "TOAST"

fresh_root; rm "$T/root/system/xbin/nemesis"
run_hook pre.sh
check "pre: no nemesis binary exits 1"  test "$RC" = 1

fresh_root
run_hook post.sh
check "post: exits 0"                   test "$RC" = 0
check "post: state dir created"         test -d "$STATE"
check "post: starts QueueService"       has "$T/stub.log" "am startservice -n hkgpackagemanager.app/.QueueService -a hkgpackagemanager.app.action.POSTINSTALL"
check "post: install log written"       test -s "$STATE/install.log"

fresh_root; PM_AM_FAIL=1; export PM_AM_FAIL
run_hook post.sh
unset PM_AM_FAIL
check "post: am failure still exits 0"  test "$RC" = 0
check "post: am failure logs WARN"      has "$STATE/install.log" "WARN"

fresh_root; mkdir -p "$STATE"; echo "k=v" > "$STATE/settings.txt"
run_hook preun.sh
check "preun: exits 0"                  test "$RC" = 0
check "preun: force-stops the app"      has "$T/stub.log" "am force-stop hkgpackagemanager.app"
check "preun: settings.txt kept"        test -f "$STATE/settings.txt"

check "lint passes on package and stick" "$ROOT/tools/lint-sh.sh" "$ROOT/package" "$ROOT/stick"

# the built zip
if [ -s "$ZIP" ]; then
    mkdir -p "$T/z/outer" "$T/z/inner"
    (cd "$T/z/outer" && unzip -q "$ZIP")
    check "outer: NEMESIS.data type=install" has "$T/z/outer/NEMESIS.data" "type=install"
    check "outer: path=packages/hkg.package.manager.zip" has "$T/z/outer/NEMESIS.data" "path=packages/hkg.package.manager.zip"
    (cd "$T/z/inner" && unzip -q "$T/z/outer/packages/hkg.package.manager.zip")
    for _f in NEMESIS.data BUILD scripts/common.sh scripts/pre.sh scripts/post.sh scripts/preun.sh app/hkgpm.apk; do
        check "inner: has $_f" test -s "$T/z/inner/$_f"
    done
    check "inner: scripts executable" test -x "$T/z/inner/scripts/pre.sh"
    check "inner: id" has "$T/z/inner/NEMESIS.data" "id=hkg.package.manager"
else
    check "zip exists ($ZIP)" false
fi

if [ $FAILS -ne 0 ]; then echo "$FAILS check(s) failed"; exit 1; fi
echo "all checks passed"
[ -s "$ZIP" ] && echo "$ZIP"
exit 0
