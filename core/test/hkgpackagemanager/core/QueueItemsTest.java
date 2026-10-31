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

    @Test public void buildsWaitingItemsAndSkipsProblems() throws Exception {
        List<StickZip> scan = Arrays.asList(
                StickZip.ok("a.zip", 10, meta("hkg.a", "1.0")),
                StickZip.problem("b.zip", 5, "not a readable zip"),
                StickZip.ok("c.zip", 12, meta("hkg.c", "2.0")));
        List<String> skipped = new ArrayList<String>();
        List<QueueItem> items = QueueItems.build(scan, new String[] {"c.zip", "b.zip", "a.zip", "gone.zip"},
                new boolean[] {true, false, false, false}, skipped);
        assertEquals(2, items.size());
        assertEquals(new QueueItem("c.zip", 0, "", "hkg.c", "2.0", true, ItemState.WAITING, ""), items.get(0));
        assertEquals(new QueueItem("a.zip", 0, "", "hkg.a", "1.0", false, ItemState.WAITING, ""), items.get(1));
        assertEquals(2, skipped.size());
        assertEquals("b.zip: not a readable zip", skipped.get(0));
        assertEquals("gone.zip: " + Messages.MSG_MISSING, skipped.get(1));
    }

    @Test public void nullPathsGiveNothing() {
        assertTrue(QueueItems.build(new ArrayList<StickZip>(), null, null, new ArrayList<String>()).isEmpty());
    }
}
