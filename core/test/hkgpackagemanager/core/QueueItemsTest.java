package hkgpackagemanager.core;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class QueueItemsTest {
    private static final Map<String, ZipFingerprint> NONE = Collections.<String, ZipFingerprint>emptyMap();

    private static PackageMeta meta(String id, String ver) throws Exception {
        return PackageMeta.from(IniFile.parse("[nemesis]\nid=" + id + "\nversion=" + ver + "\n"));
    }

    @Test public void buildsWaitingItemsAndFailedItemsForProblems() throws Exception {
        List<StickZip> scan = Arrays.asList(
                StickZip.ok("a.zip", 10, meta("hkg.a", "1.0")),
                StickZip.problem("b.zip", 5, "not a readable zip"),
                StickZip.ok("c.zip", 12, meta("hkg.c", "2.0")));
        List<QueueItem> items = QueueItems.build(scan, NONE, new String[] {"c.zip", "b.zip", "a.zip", "gone.zip"},
                new boolean[] {true, false, false, false});
        assertEquals(4, items.size());
        assertEquals(new QueueItem("c.zip", 0, "", "hkg.c", "2.0", true, ItemState.WAITING, ""), items.get(0));
        assertEquals(new QueueItem("b.zip", 0, "", "b.zip", "", false, ItemState.FAILED, "not a readable zip"), items.get(1));
        assertEquals(new QueueItem("a.zip", 0, "", "hkg.a", "1.0", false, ItemState.WAITING, ""), items.get(2));
        assertEquals(new QueueItem("gone.zip", 0, "", "gone.zip", "", false, ItemState.FAILED, Messages.MSG_MISSING), items.get(3));
    }

    @Test public void nullPathsGiveNothing() {
        assertTrue(QueueItems.build(new ArrayList<StickZip>(), NONE, null, null).isEmpty());
    }

    @Test public void expectedFingerprintFillsSizeAndHash() throws Exception {
        List<StickZip> scan = Arrays.asList(StickZip.ok("cat/a.zip", 10, meta("hkg.a", "1.0")));
        Map<String, ZipFingerprint> exp = Collections.singletonMap("cat/a.zip", new ZipFingerprint(10, "abc"));
        List<QueueItem> items = QueueItems.build(scan, exp, new String[] {"cat/a.zip"}, new boolean[] {false});
        assertEquals(new QueueItem("cat/a.zip", 10, "abc", "hkg.a", "1.0", false, ItemState.WAITING, ""), items.get(0));
    }

    @Test public void looseZipKeepsEmptyHash() throws Exception {
        List<StickZip> scan = Arrays.asList(StickZip.ok("a.zip", 10, meta("hkg.a", "1.0")),
                StickZip.ok("cat/b.zip", 11, meta("hkg.b", "1")));
        Map<String, ZipFingerprint> exp = Collections.singletonMap("cat/b.zip", new ZipFingerprint(11, "abc"));
        List<QueueItem> items = QueueItems.build(scan, exp, new String[] {"a.zip"}, null);
        assertEquals(0, items.get(0).size);
        assertEquals("", items.get(0).sha256);
    }
}
