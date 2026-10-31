#!/system/bin/sh
# hkg.package.manager postinstall (cwd: the package folder). Creates the state folder and
# tells the app to pick up any unfinished install queue. A failure there is only a warning.
. "${0%/*}/common.sh"
mkdir -p "$STATE_DIR" 2>/dev/null
chmod 755 "$STATE_DIR" 2>/dev/null
log "INFO postinstall"
if am startservice -n "$APP_PKG/.QueueService" -a "$APP_PKG.action.POSTINSTALL" > /dev/null 2>&1; then
    log "INFO QueueService started"
else
    log "WARN could not start QueueService"
fi
exit 0
