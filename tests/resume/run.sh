#!/bin/sh
# Host test for stick/resume.sh: runs it under $MOCK_SH with stubbed getprop, sleep and am.
# usage: sh tests/resume/run.sh
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
STUBS=$ROOT/tests/resume/stubs
eval "$("$ROOT/tools/toolchain.sh" env)"
: "${MOCK_SH:=sh}"
FAILS=0

check() {
    _name=$1; shift
    if "$@"; then echo "ok   $_name"; else echo "FAIL $_name"; FAILS=$((FAILS + 1)); fi
}

T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT

# run_script <zeros>: sets RC (exit status) and fresh logs in $T
run_script() {
    rm -f "$T/am.log" "$T/sleep.log" "$T/count"
    RC=0
    PM_TEST_PATH="$STUBS:/usr/bin:/bin" RESUME_ZEROS=$1 RESUME_COUNT=$T/count \
        RESUME_AM_LOG=$T/am.log RESUME_SLEEP_LOG=$T/sleep.log \
        $MOCK_SH "$ROOT/stick/resume.sh" > /dev/null 2>&1 || RC=$?
}
lines() { if [ -f "$1" ]; then _n=0; while read -r _x; do _n=$((_n + 1)); done < "$1"; echo $_n; else echo 0; fi; }
am_once_ok() { [ "$(lines "$T/am.log")" = 1 ] && [ "$(cat "$T/am.log")" = "am start -n hkgpackagemanager.app/.MainActivity --ez resume true" ]; }

run_script 0
check "booted: exit 0"            test "$RC" = 0
check "booted: am once, exact args" am_once_ok
check "booted: no sleep"          test "$(lines "$T/sleep.log")" = 0

run_script 3
check "3 zeros: exit 0"           test "$RC" = 0
check "3 zeros: three sleeps"     test "$(lines "$T/sleep.log")" = 3
check "3 zeros: am once"          am_once_ok

run_script forever
check "forever: exit 0"           test "$RC" = 0
check "forever: 150 sleeps"       test "$(lines "$T/sleep.log")" = 150
check "forever: am never called"  test ! -f "$T/am.log"

check "first line is shebang"     sh -c "[ \"\$(read -r l < '$ROOT/stick/resume.sh'; echo \"\$l\")\" = '#!/system/bin/sh' ]"
check "lint passes on stick"      "$ROOT/tools/lint-sh.sh" "$ROOT/stick"

if [ $FAILS -ne 0 ]; then echo "$FAILS check(s) failed"; exit 1; fi
echo "all checks passed"
