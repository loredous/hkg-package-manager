package hkgpackagemanager.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class QueueRunnerTest {
    private final FakeNemesis nem = new FakeNemesis();
    private final MemStickFiles stick = new MemStickFiles();
    private final List<Queue> seen = new ArrayList<Queue>();
    private final List<Queue> saved = new ArrayList<Queue>();

    private final ZipCheck check = new ZipCheck() {
        public ZipFingerprint fingerprint(String rel) {
            if (rel.startsWith("gone")) return null;
            if (rel.startsWith("chg")) return new ZipFingerprint(1, "other");
            return new ZipFingerprint(1, "s");
        }
    };
    private final QueueStore store = new QueueStore() {
        public void save(Queue q) throws IOException {
            stick.writeAtomic("q", QueueCodec.encode(q));
            saved.add(q);
        }
    };

    private QueueRunner runner() {
        return new QueueRunner(new InstallStep(nem, "/s"), store, check, new QueueRunner.Listener() {
            public void changed(Queue q) { seen.add(q); }
        });
    }

    private static QueueItem w(String rel, String id, String ver) {
        return new QueueItem(rel, 1, "s", id, ver, false, ItemState.WAITING, "");
    }

    private static Queue q(QueueItem... items) {
        return new Queue(false, java.util.Arrays.asList(items));
    }

    private int installCalls() {
        int n = 0;
        for (List<String> c : nem.calls()) if ("install".equals(c.get(0))) n++;
        return n;
    }

    @Test public void threeItemsMiddleFailsOthersInstall() {
        nem.scriptInstallSuccess("/s/a.zip", "a.a", "1");
        nem.scriptInstall("/s/b.zip", 1, "unzip failed");
        nem.scriptInstallSuccess("/s/c.zip", "c.c", "1");
        QueueRunner.Outcome o = runner().run(q(w("a.zip", "a.a", "1"), w("b.zip", "b.b", "1"), w("c.zip", "c.c", "1")));
        assertFalse(o.stopped);
        assertTrue(o.queue.finished);
        assertEquals(ItemState.DONE, o.queue.items.get(0).state);
        assertEquals(ItemState.FAILED, o.queue.items.get(1).state);
        assertEquals("install failed: unzip failed", o.queue.items.get(1).message);
        assertEquals(ItemState.DONE, o.queue.items.get(2).state);
        assertEquals(2, o.queue.doneCount());
        assertEquals(1, o.queue.failedCount());
        assertEquals(o.queue, saved.get(saved.size() - 1));
        assertEquals(o.queue, seen.get(seen.size() - 1));
        assertEquals(7, saved.size()); // 3 x (installing, result) + finish
        assertEquals(7, seen.size());
    }

    @Test public void interruptedNotInstalledIsFailedNotRetried() {
        QueueItem hung = w("a.zip", "a.a", "1").withState(ItemState.INSTALLING, "");
        Queue r = runner().resolveInterrupted(q(hung, w("b.zip", "b.b", "1")));
        assertEquals(ItemState.FAILED, r.items.get(0).state);
        assertEquals(Messages.MSG_INTERRUPTED, r.items.get(0).message);
        assertEquals(ItemState.WAITING, r.items.get(1).state);
        assertEquals(0, installCalls());
    }

    @Test public void interruptedButInstalledIsDone() {
        nem.setInstalled("a.a", "1");
        QueueItem hung = w("a.zip", "a.a", "1").withState(ItemState.INSTALLING, "");
        assertEquals(ItemState.DONE, runner().resolveInterrupted(q(hung)).items.get(0).state);
    }

    @Test public void interruptedWrongVersionIsFailed() {
        nem.setInstalled("a.a", "0");
        QueueItem hung = w("a.zip", "a.a", "1").withState(ItemState.INSTALLING, "");
        assertEquals(Messages.MSG_INTERRUPTED, runner().resolveInterrupted(q(hung)).items.get(0).message);
    }

    @Test public void changedZipIsNotInstalled() {
        QueueRunner.Outcome o = runner().run(q(w("chg.zip", "a.a", "1")));
        assertEquals(Messages.MSG_CHANGED, o.queue.items.get(0).message);
        assertEquals(ItemState.FAILED, o.queue.items.get(0).state);
        assertEquals(0, installCalls());
    }

    @Test public void missingZip() {
        QueueRunner.Outcome o = runner().run(q(w("gone.zip", "a.a", "1")));
        assertEquals(Messages.MSG_MISSING, o.queue.items.get(0).message);
        assertEquals(0, installCalls());
        assertTrue(o.queue.finished);
    }

    @Test public void saveFailureStopsBeforeNextInstall() {
        nem.scriptInstallSuccess("/s/a.zip", "a.a", "1");
        nem.scriptInstallSuccess("/s/b.zip", "b.b", "1");
        stick.failWritesAfter(2); // installing a, result a, then installing b fails
        QueueRunner.Outcome o = runner().run(q(w("a.zip", "a.a", "1"), w("b.zip", "b.b", "1")));
        assertTrue(o.stopped);
        assertEquals("stick removed", o.stopReason);
        assertEquals(1, installCalls());
        assertFalse(o.queue.finished);
    }

    @Test public void selfUpdateRunsLast() {
        nem.scriptInstallSuccess("/s/a.zip", "a.a", "1");
        nem.scriptInstallSuccess("/s/self.zip", "hkg.package.manager", "2");
        nem.scriptInstallSuccess("/s/c.zip", "c.c", "1");
        List<QueueItem> ordered = QueueOrder.order(java.util.Arrays.asList(
                w("a.zip", "a.a", "1"), w("self.zip", "hkg.package.manager", "2"), w("c.zip", "c.c", "1")));
        runner().run(new Queue(false, ordered));
        List<List<String>> calls = nem.calls();
        List<String> installs = new ArrayList<String>();
        for (List<String> c : calls) if ("install".equals(c.get(0))) installs.add(c.get(c.size() - 1));
        assertEquals("/s/self.zip", installs.get(installs.size() - 1));
    }

    @Test(expected = FakeNemesis.Rebooted.class)
    public void rebootEscapes() {
        nem.scriptInstallReboot("/s/a.zip", "a.a", "1");
        runner().run(q(w("a.zip", "a.a", "1")));
    }
}
