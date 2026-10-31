package hkgpackagemanager.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class ScreenTextTest {
    private static StickZip zip(String id, String version) throws Exception {
        IniFile ini = IniFile.parse("[nemesis]\nid=" + id + "\nversion=" + version + "\n");
        return StickZip.ok("packages/a.zip", 1, PackageMeta.from(ini));
    }

    private static Map<String, InstalledPackage> installed(String id, String version) {
        Map<String, InstalledPackage> m = new HashMap<String, InstalledPackage>();
        m.put(id, new InstalledPackage(new PkgListEntry(id, id, "", false), version));
        return m;
    }

    @Test public void statuses() throws Exception {
        Map<String, InstalledPackage> none = new HashMap<String, InstalledPackage>();
        assertEquals("New", ScreenText.statusText(zip("a.b", "2"), none));
        assertEquals("Same version", ScreenText.statusText(zip("a.b", "2"), installed("a.b", "2")));
        assertEquals("Update (installed 1, drive 2)", ScreenText.statusText(zip("a.b", "2"), installed("a.b", "1")));
        assertEquals("Update (installed unknown, drive 2)", ScreenText.statusText(zip("a.b", "2"), installed("a.b", null)));
        assertEquals("not a readable zip", ScreenText.statusText(StickZip.problem("x.zip", 1, "not a readable zip"), none));
    }

    @Test public void actionLabels() {
        assertEquals("Update", ScreenText.actionLabel(ZipStatus.UPDATE));
        assertEquals("Install", ScreenText.actionLabel(ZipStatus.NEW));
        assertEquals("Install", ScreenText.actionLabel(ZipStatus.SAME));
    }

    @Test public void summaries() {
        QueueItem done = new QueueItem("a.zip", 0, "", "a.b", "1", false, ItemState.DONE, "");
        QueueItem bad = new QueueItem("b.zip", 0, "", "c.d", "1", false, ItemState.FAILED, "changed on the drive");
        Queue q = new Queue(true, Arrays.asList(done, bad, done));
        assertEquals("Finished: 2 installed, 1 failed", ScreenText.finishedLine(q));
        assertEquals("Stopped: disk full. Plug the drive back in to continue.", ScreenText.stoppedLine("disk full"));
        assertEquals("Bad: failed - changed on the drive", ScreenText.itemLine(bad, "Bad"));
        assertEquals("Ok: done", ScreenText.itemLine(done, "Ok"));
    }

    @Test public void stickChoice() {
        assertNull(ScreenText.chooseStick(Arrays.<String>asList(), "/storage/usb1"));
        assertEquals("/storage/usb1", ScreenText.chooseStick(Arrays.asList("/storage/usb0", "/storage/usb1"), "/storage/usb1"));
        assertEquals("/storage/usb0", ScreenText.chooseStick(Arrays.asList("/storage/usb0", "/storage/usb1"), "/storage/usb2"));
        assertEquals("/storage/usb0", ScreenText.chooseStick(Arrays.asList("/storage/usb0"), null));
        assertEquals(Arrays.asList("/storage/usb1", "/storage/usb0"),
                ScreenText.preferredFirst(Arrays.asList("/storage/usb0", "/storage/usb1"), "/storage/usb1"));
    }

    @Test public void messages() {
        assertEquals("Remove the other USB drive (/storage/usb1) first: NEMESIS only reads one drive.",
                ScreenText.otherDriveMessage("/storage/usb1"));
        assertEquals("removed x", ScreenText.uninstallResult("removed x\n"));
        assertEquals("please reboot\nRestart the head unit to finish.", ScreenText.uninstallResult("please reboot"));
        assertEquals("", ScreenText.uninstallResult(null));
    }
}
