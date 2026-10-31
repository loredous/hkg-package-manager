#!/system/bin/sh
# Reopens HKG Package Manager after the head unit restarts in the middle of a multi-install.
# NEMESIS runs this from the USB drive. It always exits 0: a non-zero exit makes NEMESIS
# show a failure dialog.
#
# Host tests set PM_TEST_PATH (stub commands).
export PATH=${PM_TEST_PATH:+$PM_TEST_PATH:}/sbin:/vendor/bin:/system/sbin:/system/bin:/system/xbin

# wait for the system to finish booting: 150 tries, 2 s apart (300 s)
n=0
while [ "$(getprop sys.boot_completed)" != 1 ]; do
    n=$((n + 1))
    [ "$n" -gt 150 ] && exit 0
    sleep 2
done

am start -n hkgpackagemanager.app/.MainActivity --ez resume true > /dev/null 2>&1
exit 0
