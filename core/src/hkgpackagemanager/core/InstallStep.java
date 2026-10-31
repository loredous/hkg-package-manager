package hkgpackagemanager.core;

import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.Set;

/** Installs one queue item through NEMESIS and verifies the result. */
public final class InstallStep {
    private final Nemesis nemesis;
    private final String stickRoot;

    public InstallStep(Nemesis nemesis, String stickRoot) {
        this.nemesis = nemesis;
        this.stickRoot = stickRoot;
    }

    /** Returns the item as DONE (empty message) or FAILED (with the reason). */
    public QueueItem perform(QueueItem item) {
        String abs = stickRoot + "/" + item.relPath;
        if (!NemesisCalls.fitsDaemon(NemesisCalls.install(abs, item.update))) {
            return item.withState(ItemState.FAILED, Messages.MSG_PATH_TOO_LONG);
        }
        CliResult r = nemesis.install(abs, item.update);
        if (r.exit == 0) {
            if (!installedIds().contains(item.id)) {
                return item.withState(ItemState.FAILED, Messages.MSG_NOT_LISTED);
            }
            String got = installedVersion(item.id);
            String want = item.version == null ? "" : item.version.trim();
            if (got != null && got.trim().equals(want)) return item.withState(ItemState.DONE, "");
            return item.withState(ItemState.FAILED, Messages.versionMismatch(got, want));
        }
        if (item.update && !installedIds().contains(item.id)) {
            return item.withState(ItemState.FAILED, Messages.MSG_UPDATE_GONE);
        }
        return item.withState(ItemState.FAILED, Messages.installFailed(r));
    }

    /** The installed version from the package's NEMESIS.data, or null if unreadable. */
    public String installedVersion(String id) {
        if (!PackageId.isValid(id)) return null;
        CliResult r = nemesis.root(NemesisCalls.catCommand(NemesisCalls.installedMetaPath(id)));
        if (r.exit != 0) return null;
        String v = IniFile.parse(r.output).get("nemesis", "version");
        return v == null ? null : v.trim();
    }

    public Set<String> installedIds() {
        Set<String> ids = new HashSet<String>();
        CliResult r = nemesis.pkgList();
        if (r.exit != 0) return ids;
        for (PkgListEntry e : PkgList.parse(r.output)) ids.add(e.id);
        return ids;
    }
}
