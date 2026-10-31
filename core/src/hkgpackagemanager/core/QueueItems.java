package hkgpackagemanager.core;

import java.util.ArrayList;
import java.util.List;

/** Builds the waiting queue items for a start request from a fresh scan of the stick. */
public final class QueueItems {
    private QueueItems() {}

    /**
     * One item per requested path, taking id and version from the scan: WAITING when the zip is fine,
     * FAILED (message = the problem) when it is missing or has a problem now, so the final report lists
     * it. Size and hash stay empty: {@link QueueSession#start} fills them in for WAITING items.
     */
    public static List<QueueItem> build(List<StickZip> scanned, String[] paths, boolean[] updates) {
        List<QueueItem> out = new ArrayList<QueueItem>();
        if (paths == null) return out;
        for (int i = 0; i < paths.length; i++) {
            StickZip z = null;
            for (StickZip s : scanned) {
                if (s.relPath.equals(paths[i])) { z = s; break; }
            }
            boolean update = updates != null && i < updates.length && updates[i];
            if (z == null) {
                out.add(new QueueItem(paths[i], 0, "", paths[i], "", update, ItemState.FAILED, Messages.MSG_MISSING));
            } else if (z.problem != null || z.meta == null) {
                out.add(new QueueItem(paths[i], 0, "", z.meta != null ? z.meta.id : paths[i], "", update,
                        ItemState.FAILED, z.problem != null ? z.problem : "not a readable zip"));
            } else {
                out.add(new QueueItem(z.relPath, 0, "", z.meta.id, z.meta.version, update,
                        ItemState.WAITING, ""));
            }
        }
        return out;
    }
}
