#!/system/bin/sh
# hkg.package.manager - shared helpers for the install and uninstall hooks. Sourced by
# pre.sh, post.sh and preun.sh. Runs as root under the head unit's mksh/toolbox: no printf,
# tail, head, seq, awk, grep, [[ ]].
# PM_SYSROOT (a path prefix for every absolute path written) and PM_TEST_PATH (put first on
# PATH) exist only so tests/package/run.sh can run the hooks against a fake root with stubs.
PM_SYSROOT=${PM_SYSROOT:-}
export PATH=${PM_TEST_PATH:+$PM_TEST_PATH:}/data/nemesis/bin:/sbin:/vendor/bin:/system/sbin:/system/bin:/system/xbin
export LD_LIBRARY_PATH=/vendor/lib:/system/lib

ID=hkg.package.manager
APP_PKG=hkgpackagemanager.app
NEMESIS=$PM_SYSROOT/system/xbin/nemesis
# settings live here and survive updates and uninstalls: no hook ever removes it
STATE_DIR=$PM_SYSROOT/data/nemesis/hkg.package.manager
LOG=$STATE_DIR/install.log

log() { echo "$1"; mkdir -p "$STATE_DIR" 2>/dev/null; echo "$1" >> "$LOG" 2>/dev/null; true; }

toast() {
    am broadcast -a com.hkmc.intent.action.TOAST \
        -e com.hkmc.intent.extra.TOAST_MESSAGE "$1" \
        --ez com.hkmc.intent.extra.TOAST_POPUPSHOWING true > /dev/null 2>&1 || true
}
