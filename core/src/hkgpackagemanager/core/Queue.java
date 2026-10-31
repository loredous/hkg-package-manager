package hkgpackagemanager.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The multi-install todo list. Immutable. */
public final class Queue {
    public final boolean finished;
    public final List<QueueItem> items;

    public Queue(boolean finished, List<QueueItem> items) {
        this.finished = finished;
        this.items = Collections.unmodifiableList(new ArrayList<QueueItem>(items));
    }

    public Queue withItem(int index, QueueItem item) {
        List<QueueItem> l = new ArrayList<QueueItem>(items);
        l.set(index, item);
        return new Queue(finished, l);
    }

    public Queue finish() {
        return new Queue(true, items);
    }

    /** Index of the first WAITING item, or -1. */
    public int nextWaiting() {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).state == ItemState.WAITING) return i;
        }
        return -1;
    }

    public int doneCount() { return count(ItemState.DONE); }

    public int failedCount() { return count(ItemState.FAILED); }

    private int count(ItemState s) {
        int n = 0;
        for (QueueItem i : items) if (i.state == s) n++;
        return n;
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Queue)) return false;
        Queue b = (Queue) o;
        return finished == b.finished && items.equals(b.items);
    }

    @Override public int hashCode() {
        return items.hashCode() * 31 + (finished ? 1 : 0);
    }
}
