package hkgpackagemanager.core;

import java.util.ArrayList;
import java.util.List;

/** Builds the waiting queue items for a start request from a fresh scan of the stick. */
public final class QueueItems {
    private QueueItems() {}

    /**
     * One WAITING item per requested path, taking id and version from the scan. A path that is not in
     * the scan, or whose zip has a problem, is left out and a line about it is added to {@code skipped}.
     * Size and hash stay empty: {@link QueueSession#start} fills them in.
     */
    public static List<QueueItem> build(List<StickZip> scanned, String[] paths, boolean[] updates,
            List<String> skipped) {
        List<QueueItem> out = new ArrayList<QueueItem>();
        if (paths == null) return out;
        for (int i = 0; i < paths.length; i++) {
            StickZip z = null;
            for (StickZip s : scanned) {
                if (s.relPath.equals(paths[i])) { z = s; break; }
            }
            if (z == null) {
                skipped.add(paths[i] + ": " + Messages.MSG_MISSING);
            } else if (z.problem != null || z.meta == null) {
                skipped.add(paths[i] + ": " + (z.problem != null ? z.problem : "unreadable"));
            } else {
                boolean update = updates != null && i < updates.length && updates[i];
                out.add(new QueueItem(z.relPath, 0, "", z.meta.id, z.meta.version, update,
                        ItemState.WAITING, ""));
            }
        }
        return out;
    }
}
