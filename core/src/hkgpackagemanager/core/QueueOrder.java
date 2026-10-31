package hkgpackagemanager.core;

import java.util.ArrayList;
import java.util.List;

/** Install order: the user's order, except the app's own update goes last. */
public final class QueueOrder {
    public static final String SELF_ID = "hkg.package.manager";

    private QueueOrder() {}

    public static List<QueueItem> order(List<QueueItem> items) {
        List<QueueItem> out = new ArrayList<QueueItem>();
        List<QueueItem> self = new ArrayList<QueueItem>();
        for (QueueItem i : items) {
            if (SELF_ID.equals(i.id)) self.add(i); else out.add(i);
        }
        out.addAll(self);
        return out;
    }
}
