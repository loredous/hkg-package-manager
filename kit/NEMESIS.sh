#!/system/bin/sh
# hkg key-capture kit - READ ONLY. Changes nothing on the car.
#
# Finds out which broadcast each hardware button sends, and writes KEYCAP/ on this USB drive:
#   summary.txt     the product id; ends with "done" when the kit finished
#   keys.txt        only the log lines about key events and shortcuts: the lines before the
#                   drive was inserted, then 60 seconds of live log while you press buttons
#   broadcasts.txt  only the recent-broadcast lines naming a _SHORTCUT or CUSTOM action
#
# Nothing else from the Android log is kept (it can hold contacts, Bluetooth addresses and
# other ids); the unfiltered log only touches a temporary file that is deleted at the end.
# No vehicle commands are sent. Nothing is copied from /data.
#
# Host tests set KIT_TEST_PATH (stub commands) and KIT_TMP (temp folder).
export PATH=${KIT_TEST_PATH:+$KIT_TEST_PATH:}/sbin:/vendor/bin:/system/sbin:/system/bin:/system/xbin
export LD_LIBRARY_PATH=/vendor/lib:/system/lib

# The drive is the folder this script is in: NEMESIS's USB handler starts it from the drive
# root, but NEMESIS Manager's "Run USB NEMESIS.sh" calls it by full path from elsewhere.
case "$0" in */*) KIT=${0%/*} ;; *) KIT=. ;; esac
KIT=$(cd "$KIT" && pwd) || exit 1
OUT=$KIT/KEYCAP
SUM=$OUT/summary.txt
KEYS=$OUT/keys.txt
BCAST=$OUT/broadcasts.txt
TMP=${KIT_TMP:-/data/local/tmp}
PRE=$TMP/hkg-keycap-pre.txt
LIVE=$TMP/hkg-keycap-live.txt

rm -r "$OUT" 2>/dev/null
mkdir -p "$OUT" || exit 1
: > "$SUM" && : > "$KEYS" && : > "$BCAST" || exit 1

toast() {
    am broadcast -a com.hkmc.intent.action.TOAST \
        -e com.hkmc.intent.extra.TOAST_MESSAGE "$1" \
        --ez com.hkmc.intent.extra.TOAST_POPUPSHOWING true > /dev/null 2>&1
}

# keep only key and shortcut lines from the log file $1
filter_keys() {
    while read -r _l; do
        case "$_l" in
            *ModeKeyDispatcher*|*KEYCODE_*|*_SHORTCUT*|*"send key"*) echo "$_l" >> "$KEYS" ;;
        esac
    done < "$1"
}

# presses made just before the drive was inserted
logcat -d -v time > "$PRE" 2>&1

echo "product $(getprop ro.build.product)" >> "$SUM"
toast "Key capture: press each button now (60 seconds)"

logcat -v time > "$LIVE" 2>&1 &
_pid=$!
sleep 60
kill $_pid 2>/dev/null
wait $_pid 2>/dev/null

filter_keys "$PRE"
filter_keys "$LIVE"

dumpsys activity broadcasts > "$TMP/hkg-keycap-bc.txt" 2>&1
while read -r _l; do
    case "$_l" in
        *_SHORTCUT*|*CUSTOM*) echo "$_l" >> "$BCAST" ;;
    esac
done < "$TMP/hkg-keycap-bc.txt"

rm -f "$PRE" "$LIVE" "$TMP/hkg-keycap-bc.txt"
echo "done" >> "$SUM"
sync
toast "Key capture finished, unplug the drive"
exit 0
