package hkgpackagemanager.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class NemesisCallsTest {
    private static List<String> list(String s) { return Collections.singletonList(s); }
    private static String repeat(char c, int n) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n; i++) b.append(c);
        return b.toString();
    }

    @Test public void installPassesPathAsOneArgument() {
        assertEquals(Arrays.asList("install", "/storage/usb0/Enable ADB.zip"), NemesisCalls.install("/storage/usb0/Enable ADB.zip", false));
        assertEquals(Arrays.asList("install", "--update", "/x.zip"), NemesisCalls.install("/x.zip", true));
    }
    @Test public void otherCalls() {
        assertEquals(Arrays.asList("uninstall", "hkg.x"), NemesisCalls.uninstall("hkg.x"));
        assertEquals(Arrays.asList("pkg-list"), NemesisCalls.pkgList());
        assertEquals(Arrays.asList("root", "-c", "id"), NemesisCalls.root("id"));
        assertEquals("cat '/a b'", NemesisCalls.catCommand("/a b"));
    }
    @Test public void copyCommand() {
        assertEquals("mkdir -p '/storage/usb0/HKGPM' && cp '/data/data/hkgpackagemanager.app/files/s/q' '/storage/usb0/HKGPM/queue.json.tmp'"
            + " && mv '/storage/usb0/HKGPM/queue.json.tmp' '/storage/usb0/HKGPM/queue.json' && sync",
            NemesisCalls.copyIntoCommand("/data/data/hkgpackagemanager.app/files/s/q", "/storage/usb0/HKGPM/queue.json"));
    }
    @Test public void daemonArgLimit() {
        assertTrue(NemesisCalls.fitsDaemon(list(repeat('a', 768))));
        assertFalse(NemesisCalls.fitsDaemon(list(repeat('a', 769))));
        assertFalse(NemesisCalls.fitsDaemon(list(repeat('é', 385))));
    }
    @Test public void metaPath() {
        assertEquals("/data/nemesis/apps/hkg.x/NEMESIS.data", NemesisCalls.installedMetaPath("hkg.x"));
    }
    @Test(expected = IllegalArgumentException.class) public void metaPathRejectsBadId() { NemesisCalls.installedMetaPath("../etc"); }
}
