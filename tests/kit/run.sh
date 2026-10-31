#!/bin/sh
# Host test for kit/NEMESIS.sh: runs the kit with stubbed Android commands and checks what
# it writes to the "drive".
# usage: sh tests/kit/run.sh
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
STUBS=$ROOT/tests/kit/stubs
FAILS=0

check() {
    _name=$1; shift
    if "$@"; then echo "ok   $_name"; else echo "FAIL $_name"; FAILS=$((FAILS + 1)); fi
}

last_line_done() { _l=; while read -r _x; do _l=$_x; done < "$1"; [ "$_l" = done ]; }
toasts_at_least_2() { [ -f "$1" ] && [ "$(grep -c "com.hkmc.intent.action.TOAST" "$1")" -ge 2 ]; }

T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT

D=$T/drive; TMPD=$T/tmp; mkdir -p "$D" "$TMPD"
cp "$ROOT/kit/NEMESIS.sh" "$D/" 2>/dev/null
(cd "$D" && KIT_TMP=$TMPD KIT_TEST_PATH="$STUBS:/usr/bin:/bin" FAKE_AM_LOG=$T/am.log FAKE_LIVE_FLAG=$T/live.flag FAKE_KILL_LOG=$T/kill.log \
    sh NEMESIS.sh > "$T/kit.out" 2>&1)
O=$D/KEYCAP

check "keys: media key line"       grep -q 'KEYCODE_SHORTCUT_MEDIA' "$O/keys.txt"
check "keys: navi shortcut line"   grep -q 'NAVI_SHORTCUT' "$O/keys.txt"
check "keys: micom line"           grep -q 'MicomD' "$O/keys.txt"
check "keys: live-only line"      grep -q KEYCODE_LIVE_ONLY "$O/keys.txt"
check "live logcat was killed"     grep -q killed "$T/kill.log"
check "keys: no contact name"      sh -c "test -s '$O/keys.txt' && ! grep -q 'Jane Doe' '$O/keys.txt'"
check "keys: no bluetooth address" sh -c "test -s '$O/keys.txt' && ! grep -q '00:11:22:33:44:55' '$O/keys.txt'"
check "keys: no unrelated lines"   sh -c "test -s '$O/keys.txt' && ! grep -q 'unrelated' '$O/keys.txt'"
check "broadcasts: media kept"     grep -q 'MEDIA_SHORTCUT' "$O/broadcasts.txt"
check "broadcasts: time tick out"  sh -c "test -s '$O/broadcasts.txt' && ! grep -q 'TIME_TICK' '$O/broadcasts.txt'"
check "summary: product line"      grep -q '^product ' "$O/summary.txt"
check "summary: ends with done"    last_line_done "$O/summary.txt"
check "toast at start and finish"  toasts_at_least_2 "$T/am.log"
check "temp files removed"         sh -c "test -s '$O/summary.txt' && test -z \"\$(ls '$TMPD')\""
check "lint passes on kit"         "$ROOT/tools/lint-sh.sh" "$ROOT/kit"

if [ $FAILS -ne 0 ]; then echo "$FAILS check(s) failed"; exit 1; fi
echo "all checks passed"
