#!/system/bin/sh
# hkg.package.manager preuninstall (cwd: the package folder). NEMESIS also runs this on
# updates, so it must not remove the state folder: settings survive updates and uninstalls.
. "${0%/*}/common.sh"
log "INFO preuninstall"
am force-stop "$APP_PKG" > /dev/null 2>&1 || log "WARN could not stop the app"
exit 0
