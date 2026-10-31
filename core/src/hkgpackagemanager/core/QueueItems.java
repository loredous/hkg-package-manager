package hkgpackagemanager.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Builds the waiting queue items for a start request from a fresh scan of the stick. */
public final class QueueItems {
    private QueueItems() {}

    /**
     * One item per requested path, taking id and version from the scan: WAITING when the zip is fine,
     * FAILED (message = the problem) when it is missing or has a problem now, so the final report lists
     * it. A zip with an expected fingerprint (a catalog zip) gets its size and hash now, and
     * {@link QueueSession#start} refuses it if the file differs; others stay 0 / "" and start fills them in.
     */
    public static List<QueueItem> build(List<StickZip> scanned, Map<String, ZipFingerprint> expected, String[] paths, boolean[] updates) {
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
                ZipFingerprint want = expected.get(z.relPath);
                out.add(new QueueItem(z.relPath, want == null ? 0 : want.size, want == null ? "" : want.sha256,
                        z.meta.id, z.meta.version, update,
                        ItemState.WAITING, ""));
            }
        }
        return out;
    }
}
