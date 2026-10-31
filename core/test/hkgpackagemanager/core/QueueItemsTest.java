package hkgpackagemanager.core;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class QueueItemsTest {
    private static PackageMeta meta(String id, String ver) throws Exception {
        return PackageMeta.from(IniFile.parse("[nemesis]\nid=" + id + "\nversion=" + ver + "\n"));
    }

    @Test public void buildsWaitingItemsAndFailedItemsForProblems() throws Exception {
        List<StickZip> scan = Arrays.asList(
                StickZip.ok("a.zip", 10, meta("hkg.a", "1.0")),
                StickZip.problem("b.zip", 5, "not a readable zip"),
                StickZip.ok("c.zip", 12, meta("hkg.c", "2.0")));
        List<QueueItem> items = QueueItems.build(scan, new String[] {"c.zip", "b.zip", "a.zip", "gone.zip"},
                new boolean[] {true, false, false, false});
        assertEquals(4, items.size());
        assertEquals(new QueueItem("c.zip", 0, "", "hkg.c", "2.0", true, ItemState.WAITING, ""), items.get(0));
        assertEquals(new QueueItem("b.zip", 0, "", "b.zip", "", false, ItemState.FAILED, "not a readable zip"), items.get(1));
        assertEquals(new QueueItem("a.zip", 0, "", "hkg.a", "1.0", false, ItemState.WAITING, ""), items.get(2));
        assertEquals(new QueueItem("gone.zip", 0, "", "gone.zip", "", false, ItemState.FAILED, Messages.MSG_MISSING), items.get(3));
    }

    @Test public void nullPathsGiveNothing() {
        assertTrue(QueueItems.build(new ArrayList<StickZip>(), null, null).isEmpty());
    }
}
