package hkgpackagemanager.core;

import java.io.IOException;
import java.util.List;

/** Walks the queue in order, saving every state change before moving on. */
public final class QueueRunner {
    public interface Listener {
        void changed(Queue q);
    }

    public static final class Outcome {
        public final Queue queue;
        public final boolean stopped;
        public final String stopReason;

        public Outcome(Queue queue, boolean stopped, String stopReason) {
            this.queue = queue;
            this.stopped = stopped;
            this.stopReason = stopReason;
        }
    }

    private final InstallStep step;
    private final QueueStore store;
    private final ZipCheck check;
    private final Listener listener;

    public QueueRunner(InstallStep step, QueueStore store, ZipCheck check, Listener listener) {
        this.step = step;
        this.store = store;
        this.check = check;
        this.listener = listener;
    }

    /** Runs every WAITING item. A failed save stops the run at once; Nemesis exceptions are not caught. */
    public Outcome run(Queue q) {
        try {
            for (int i = q.nextWaiting(); i >= 0; i = q.nextWaiting()) {
                QueueItem item = q.items.get(i);
                String problem = problemWithZip(item);
                if (problem != null) {
                    q = save(q.withItem(i, item.withState(ItemState.FAILED, problem)));
                    continue;
                }
                QueueItem running = item.withState(ItemState.INSTALLING, "");
                q = save(q.withItem(i, running));
                q = save(q.withItem(i, step.perform(running)));
            }
            q = save(q.finish());
            return new Outcome(q, false, null);
        } catch (StopRun e) {
            return new Outcome(e.last, true, e.getMessage());
        }
    }

    /** Settles items left INSTALLING by a restart: DONE if installed at the right version, else FAILED. Never retries. */
    public Queue resolveInterrupted(Queue q) {
        List<QueueItem> items = q.items;
        java.util.Set<String> ids = null;
        for (int i = 0; i < items.size(); i++) {
            QueueItem item = items.get(i);
            if (item.state != ItemState.INSTALLING) continue;
            if (ids == null) ids = step.installedIds();
            boolean ok = ids.contains(item.id) && item.version != null
                    && item.version.trim().equals(step.installedVersion(item.id));
            q = q.withItem(i, ok ? item.withState(ItemState.DONE, "")
                    : item.withState(ItemState.FAILED, Messages.MSG_INTERRUPTED));
        }
        return q;
    }

    private String problemWithZip(QueueItem item) {
        ZipFingerprint f;
        try {
            f = check.fingerprint(item.relPath);
        } catch (IOException e) {
            return Messages.MSG_MISSING;
        }
        if (f == null) return Messages.MSG_MISSING;
        if (f.size != item.size || !f.sha256.equals(item.sha256)) return Messages.MSG_CHANGED;
        return null;
    }

    private Queue save(Queue q) throws StopRun {
        try {
            store.save(q);
        } catch (IOException e) {
            throw new StopRun(String.valueOf(e.getMessage()), q);
        }
        listener.changed(q);
        return q;
    }

    private static final class StopRun extends Exception {
        final Queue last;

        StopRun(String why, Queue last) {
            super(why);
            this.last = last;
        }
    }
}
