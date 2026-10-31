#!/system/bin/sh
# hkg.package.manager preinstall (cwd: NEMESIS staging). Fails unless the NEMESIS command
# channel works for an ordinary app uid: that is how the manager does all its root work.
. "${0%/*}/common.sh"
log "INFO preinstall"
if [ -x "$NEMESIS" ] && "$NEMESIS" root 10999 -c '/system/xbin/nemesis pkg-list' > /dev/null 2>&1; then
    log "INFO command channel ok"
    exit 0
fi
log "ERROR NEMESIS command channel not available"
toast "HKG Package Manager needs NEMESIS with its command channel running."
exit 1
