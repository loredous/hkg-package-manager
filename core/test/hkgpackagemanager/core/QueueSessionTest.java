package hkgpackagemanager.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class QueueSessionTest {
    private final FakeNemesis nem = new FakeNemesis();
    private final MemStickFiles stick = new MemStickFiles();
    private final byte[] script = "#!/bin/sh\n".getBytes();
    private final List<Queue> seen = new ArrayList<Queue>();

    private final ZipCheck check = new ZipCheck() {
        public ZipFingerprint fingerprint(String rel) {
            return rel.startsWith("gone") ? null : new ZipFingerprint(5, "sha-" + rel);
        }
    };

    private QueueSession session() {
        return new QueueSession(stick, "/s", nem, check, script, new QueueRunner.Listener() {
            public void changed(Queue q) { seen.add(q); }
        });
    }

    private static QueueItem sel(String rel, String id, String ver) {
        return new QueueItem(rel, 0, "", id, ver, false, ItemState.WAITING, "");
    }

    private int installCalls() {
        int n = 0;
        for (List<String> c : nem.calls()) if ("install".equals(c.get(0))) n++;
        return n;
    }

    @Test public void startRunsAndRestoresDescriptor() throws Exception {
        stick.put("NEMESIS.data", "[nemesis]\nid=stick\n");
        nem.scriptInstallSuccess("/s/a.zip", "a.a", "1");
        QueueRunner.Outcome o = session().start(Arrays.asList(sel("a.zip", "a.a", "1")));
        assertFalse(o.stopped);
        assertTrue(o.queue.finished);
        assertEquals(5, o.queue.items.get(0).size);
        assertEquals("sha-a.zip", o.queue.items.get(0).sha256);
        assertEquals("[nemesis]\nid=stick\n", stick.text("NEMESIS.data"));
        assertFalse(stick.exists("HKGPM/resume.sh"));
        assertNull(session().loadUnfinished());
    }

    @Test public void missingAtStartIsFailedItem() throws Exception {
        nem.scriptInstallSuccess("/s/a.zip", "a.a", "1");
        QueueRunner.Outcome o = session().start(Arrays.asList(sel("gone.zip", "g.g", "1"), sel("a.zip", "a.a", "1")));
        assertEquals(Messages.MSG_MISSING, o.queue.items.get(0).message);
        assertEquals(ItemState.DONE, o.queue.items.get(1).state);
    }

    @Test public void startKeepsPreFailedItemsAndDoesNotInstallThem() throws Exception {
        nem.scriptInstallSuccess("/s/a.zip", "a.a", "1");
        QueueItem bad = new QueueItem("bad.zip", 0, "", "bad.zip", "", false, ItemState.FAILED, "not a readable zip");
        QueueRunner.Outcome o = session().start(Arrays.asList(bad, sel("a.zip", "a.a", "1")));
        assertEquals(bad, o.queue.items.get(0));
        assertEquals(ItemState.DONE, o.queue.items.get(1).state);
        assertEquals(1, installCalls());
    }

    @Test public void startWithOnlyFailedItemsStillFinishes() throws Exception {
        QueueItem bad = new QueueItem("bad.zip", 0, "", "bad.zip", "", false, ItemState.FAILED, "x");
        QueueRunner.Outcome o = session().start(Arrays.asList(bad));
        assertFalse(o.stopped);
        assertTrue(o.queue.finished);
        assertEquals(0, installCalls());
    }

    @Test public void startOrdersSelfLast() throws Exception {
        nem.scriptInstallSuccess("/s/a.zip", "a.a", "1");
        nem.scriptInstallSuccess("/s/self.zip", "hkg.package.manager", "2");
        QueueRunner.Outcome o = session().start(Arrays.asList(sel("self.zip", "hkg.package.manager", "2"), sel("a.zip", "a.a", "1")));
        assertEquals("hkg.package.manager", o.queue.items.get(1).id);
    }

    @Test public void rebootMidQueueThenResume() throws Exception {
        stick.put("NEMESIS.data", "[nemesis]\nid=stick\n");
        nem.scriptInstallSuccess("/s/a.zip", "a.a", "1");
        nem.scriptInstallReboot("/s/b.zip", "b.b", "1");
        nem.scriptInstallSuccess("/s/c.zip", "c.c", "1");
        try {
            session().start(Arrays.asList(sel("a.zip", "a.a", "1"), sel("b.zip", "b.b", "1"), sel("c.zip", "c.c", "1")));
            fail("expected reboot");
        } catch (FakeNemesis.Rebooted expected) {
            // process died
        }
        assertTrue(stick.exists("HKGPM/resume.sh"));
        QueueSession s2 = session();
        Queue pending = s2.loadUnfinished();
        assertEquals(ItemState.INSTALLING, pending.items.get(1).state);
        QueueRunner.Outcome o = s2.resume();
        assertEquals(ItemState.DONE, o.queue.items.get(0).state);
        assertEquals(ItemState.DONE, o.queue.items.get(1).state);
        assertEquals(ItemState.DONE, o.queue.items.get(2).state);
        assertTrue(o.queue.finished);
        assertEquals(3, installCalls());
        assertEquals("[nemesis]\nid=stick\n", stick.text("NEMESIS.data"));
        assertFalse(stick.exists("HKGPM/resume.sh"));
    }

    @Test public void resumeWithNothingReturnsNull() throws Exception {
        assertNull(session().resume());
    }

    @Test public void startRefusesWhenUnfinishedQueueExists() throws Exception {
        stick.writeAtomic("HKGPM/queue.json", QueueCodec.encode(new Queue(false, Arrays.asList(sel("a.zip", "a.a", "1")))));
        try {
            session().start(Arrays.asList(sel("a.zip", "a.a", "1")));
            fail("expected refusal");
        } catch (IllegalStateException expected) {
            // ok
        }
        assertEquals(0, installCalls());
    }

    @Test public void discardMarksRemainingAndRestoresDescriptor() throws Exception {
        stick.put("NEMESIS.data", "[nemesis]\nid=stick\n");
        DescriptorSwap.begin(stick, script);
        Queue q = new Queue(false, Arrays.asList(
                sel("a.zip", "a.a", "1").withState(ItemState.DONE, ""),
                sel("b.zip", "b.b", "1").withState(ItemState.INSTALLING, ""),
                sel("c.zip", "c.c", "1")));
        stick.writeAtomic("HKGPM/queue.json", QueueCodec.encode(q));
        session().discard();
        Queue r = QueueCodec.decode(stick.read("HKGPM/queue.json"));
        assertTrue(r.finished);
        assertEquals(ItemState.DONE, r.items.get(0).state);
        assertEquals(Messages.MSG_INTERRUPTED, r.items.get(1).message);
        assertEquals(Messages.MSG_DISCARDED, r.items.get(2).message);
        assertEquals(ItemState.FAILED, r.items.get(2).state);
        assertEquals("[nemesis]\nid=stick\n", stick.text("NEMESIS.data"));
        assertFalse(stick.exists("HKGPM/resume.sh"));
    }

    @Test public void loadUnfinishedReturnsNullForFinishedQueue() throws Exception {
        assertNull(session().loadUnfinished());
        stick.writeAtomic("HKGPM/queue.json", QueueCodec.encode(new Queue(true, Arrays.asList(sel("a.zip", "a.a", "1")))));
        assertNull(session().loadUnfinished());
    }

    @Test public void loadUnfinishedUnreadablePropagates() throws Exception {
        stick.put("HKGPM/queue.json", "not json");
        try {
            session().loadUnfinished();
            fail("expected Unreadable");
        } catch (QueueCodec.Unreadable expected) {
            // ok
        }
    }

    @Test public void discardResolvesInstallingItemThatDidInstall() throws Exception {
        nem.setInstalled("b.b", "1");
        Queue q = new Queue(false, Arrays.asList(sel("b.zip", "b.b", "1").withState(ItemState.INSTALLING, "")));
        stick.writeAtomic("HKGPM/queue.json", QueueCodec.encode(q));
        session().discard();
        assertEquals(ItemState.DONE, QueueCodec.decode(stick.read("HKGPM/queue.json")).items.get(0).state);
    }

    @Test public void discardUnreadableQueueMovesItAside() throws Exception {
        stick.put("HKGPM/queue.json", "garbage");
        stick.put("HKGPM/queue.json.bad", "older");
        session().discard();
        assertFalse(stick.exists("HKGPM/queue.json"));
        assertEquals("garbage", stick.text("HKGPM/queue.json.bad"));
        assertNull(session().loadUnfinished());
        nem.scriptInstallSuccess("/s/a.zip", "a.a", "1");
        assertTrue(session().start(Arrays.asList(sel("a.zip", "a.a", "1"))).queue.finished);
    }

    @Test public void resumeWithFinishedQueueRemovesOurLeftoverDescriptor() throws Exception {
        stick.put("NEMESIS.data", "[nemesis]\nid=stick\n");
        DescriptorSwap.begin(stick, script);
        stick.writeAtomic("HKGPM/queue.json", QueueCodec.encode(new Queue(true, Arrays.asList(sel("a.zip", "a.a", "1")))));
        assertNull(session().resume());
        assertEquals("[nemesis]\nid=stick\n", stick.text("NEMESIS.data"));
        assertFalse(stick.exists("HKGPM/resume.sh"));
    }
}
