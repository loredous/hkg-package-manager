package hkgpackagemanager.core;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import org.junit.Test;

public class QueueTest {
    private static QueueItem it(ItemState s) {
        return new QueueItem("a.zip", 1, "x", "i", "1", false, s, "");
    }

    @Test public void countsAndNext() {
        Queue q = new Queue(false, Arrays.asList(it(ItemState.DONE), it(ItemState.FAILED), it(ItemState.WAITING)));
        assertEquals(1, q.doneCount());
        assertEquals(1, q.failedCount());
        assertEquals(2, q.nextWaiting());
        Queue q2 = q.withItem(2, it(ItemState.WAITING).withState(ItemState.DONE, "ok"));
        assertEquals(-1, q2.nextWaiting());
        assertEquals("ok", q2.items.get(2).message);
        assertEquals(ItemState.WAITING, q.items.get(2).state);
        assertEquals(true, q2.finish().finished);
    }

    @Test public void messagesFormat() {
        assertEquals("install failed: boom", Messages.installFailed(new CliResult(1, "x\nboom\n")));
        assertEquals("install failed: exit 3", Messages.installFailed(new CliResult(3, "")));
        assertEquals("installed version is 1, expected 2", Messages.versionMismatch("1", "2"));
    }
}
