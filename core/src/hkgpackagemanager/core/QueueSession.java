package hkgpackagemanager.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Starts, resumes and discards a multi-install, keeping the queue file and the stick's descriptor in step. */
public final class QueueSession {
    private final StickFiles stick;
    private final Nemesis nemesis;
    private final ZipCheck check;
    private final byte[] resumeScript;
    private final QueueRunner.Listener listener;
    private final QueueRunner runner;
    private final QueueStore store = new QueueStore() {
        public void save(Queue q) throws IOException {
            stick.writeAtomic(StickLayout.QUEUE, QueueCodec.encode(q));
        }
    };

    public QueueSession(StickFiles stick, String stickRoot, Nemesis nemesis, ZipCheck check,
            byte[] resumeScript, QueueRunner.Listener listener) {
        this.stick = stick;
        this.nemesis = nemesis;
        this.check = check;
        this.resumeScript = resumeScript;
        this.listener = listener;
        this.runner = new QueueRunner(new InstallStep(nemesis, stickRoot), store, check, listener);
    }

    public QueueRunner.Outcome start(List<QueueItem> selected) throws IOException {
        try {
            if (loadUnfinished() != null) throw new IllegalStateException("an unfinished install list exists");
        } catch (QueueCodec.Unreadable e) {
            throw new IllegalStateException("an unreadable install list exists: " + e.getMessage());
        }
        List<QueueItem> items = new ArrayList<QueueItem>();
        for (QueueItem i : QueueOrder.order(selected)) {
            if (i.state != ItemState.WAITING) { // already failed (e.g. a bad zip): keep it, never install it
                items.add(i);
                continue;
            }
            ZipFingerprint f = check.fingerprint(i.relPath);
            if (f == null) {
                items.add(new QueueItem(i.relPath, 0, "", i.id, i.version, i.update,
                        ItemState.FAILED, Messages.MSG_MISSING));
            } else {
                items.add(new QueueItem(i.relPath, f.size, f.sha256, i.id, i.version, i.update,
                        ItemState.WAITING, ""));
            }
        }
        Queue q = new Queue(false, items);
        DescriptorSwap.begin(stick, resumeScript);
        store.save(q);
        listener.changed(q);
        return finish(runner.run(q));
    }

    /** Continues an unfinished queue, or returns null when there is none. */
    public QueueRunner.Outcome resume() throws IOException {
        Queue q;
        try {
            q = loadUnfinished();
        } catch (QueueCodec.Unreadable e) {
            throw new IOException("unreadable install list: " + e.getMessage());
        }
        if (q == null) {
            if (stick.exists(StickLayout.DESCRIPTOR) && DescriptorSwap.isOurs(stick.read(StickLayout.DESCRIPTOR))) {
                DescriptorSwap.end(stick);
            }
            return null;
        }
        q = runner.resolveInterrupted(q);
        store.save(q);
        listener.changed(q);
        return finish(runner.run(q));
    }

    /** The queue file's contents when it exists and is not finished; null otherwise. */
    public Queue loadUnfinished() throws QueueCodec.Unreadable {
        if (!stick.exists(StickLayout.QUEUE)) return null;
        byte[] data;
        try {
            data = stick.read(StickLayout.QUEUE);
        } catch (IOException e) {
            throw new QueueCodec.Unreadable("cannot read: " + e.getMessage());
        }
        Queue q = QueueCodec.decode(data);
        return q.finished ? null : q;
    }

    public void discard() throws IOException {
        Queue q = null;
        try {
            q = loadUnfinished();
        } catch (QueueCodec.Unreadable e) {
            String bad = StickLayout.QUEUE + ".bad";
            if (stick.exists(bad)) stick.delete(bad);
            stick.move(StickLayout.QUEUE, bad);
        }
        if (q != null) {
            q = runner.resolveInterrupted(q);
            for (int i = 0; i < q.items.size(); i++) {
                QueueItem it = q.items.get(i);
                if (it.state == ItemState.WAITING) {
                    q = q.withItem(i, it.withState(ItemState.FAILED, Messages.MSG_DISCARDED));
                }
            }
            q = q.finish();
            store.save(q);
            listener.changed(q);
        }
        DescriptorSwap.end(stick);
    }

    private QueueRunner.Outcome finish(QueueRunner.Outcome o) throws IOException {
        if (!o.stopped) DescriptorSwap.end(stick);
        return o;
    }
}
