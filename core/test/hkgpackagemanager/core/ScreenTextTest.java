package hkgpackagemanager.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class ScreenTextTest {
    private static final Verdict GREEN = new Verdict(Colour.GREEN, "Checked");
    private static final Verdict YELLOW = new Verdict(Colour.YELLOW, "Not checked");
    private static final Verdict RED = new Verdict(Colour.RED, "Known bad");

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
        assertEquals("Same version", ScreenText.statusText(zip("a.b", ""), installed("a.b", "")));
        assertEquals("Update (installed 1, drive unknown)", ScreenText.statusText(zip("a.b", ""), installed("a.b", "1")));
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

    @Test public void descriptorNotes() {
        assertEquals("If NEMESIS asks to run an item from the USB drive, either answer is fine.",
                ScreenText.descriptorNote(true, false));
        assertEquals(ScreenText.HANDLER_NOTE, ScreenText.descriptorNote(true, true));
        assertEquals("NEMESIS may now ask about the drive's own NEMESIS.data. Answer as you would when inserting the drive.",
                ScreenText.descriptorNote(false, true));
        assertNull(ScreenText.descriptorNote(false, false));
    }

    @Test public void singleConfirmWarnsOnSelfUpdate() throws Exception {
        assertEquals("Update test.a 1.1?", ScreenText.singleConfirm("Update", zip("test.a", "1.1").meta, GREEN));
        assertEquals("Install Test A?", ScreenText.singleConfirm("Install",
                StickZip.ok("a.zip", 1, PackageMeta.from(IniFile.parse("[nemesis]\nid=test.a\nname=Test A\n"))).meta, GREEN));
        assertEquals("Update HKG Package Manager 1.0.1?\n\nThe manager will close while it updates; open it again afterwards.",
                ScreenText.singleConfirm("Update", StickZip.ok("m.zip", 1, PackageMeta.from(IniFile.parse(
                        "[nemesis]\nid=hkg.package.manager\nname=HKG Package Manager\nversion=1.0.1\n"))).meta, GREEN));
        assertEquals("An install is running.", ScreenText.RUNNING_INFO);
    }

    @Test public void gatedConfirmations() throws Exception {
        PackageMeta a = zip("test.a", "1.1").meta;
        assertEquals("Update test.a 1.1?\n\nThis package hasn't been checked for this car. (Not checked)",
                ScreenText.singleConfirm("Update", a, YELLOW));
        assertEquals("Install test.a 1.1?\n\nThis package is marked as not working on this car. (Known bad)",
                ScreenText.singleConfirm("Install", a, RED));
        PackageMeta m = zip("hkg.package.manager", "1.0.1").meta;
        assertEquals("Update hkg.package.manager 1.0.1?\n\n" + ScreenText.SELF_UPDATE_WARNING
                + "\n\nThis package hasn't been checked for this car. (Not from a catalog)",
                ScreenText.singleConfirm("Update", m, CompatCheck.LOOSE));
        assertEquals("Install these packages?\n\nA 1\nB 2",
                ScreenText.queueConfirm(Arrays.asList("A 1", "B 2"), java.util.Collections.<String>emptyList(), 0));
        assertEquals("Install these packages?\n\nA 1\n\nNot checked for this car: A, B"
                + "\n\nTick each package marked as not working on this car to continue.",
                ScreenText.queueConfirm(Arrays.asList("A 1"), Arrays.asList("A", "B"), 2));
        assertEquals("Install these packages?\n\nA 1\n\nTick each package marked as not working on this car to continue.",
                ScreenText.queueConfirm(Arrays.asList("A 1"), java.util.Collections.<String>emptyList(), 1));
        assertEquals("Known bad", ScreenText.verdictLine(RED));
    }

    @Test public void trustWording() {
        assertEquals("0123 4567 89ab cdef", ScreenText.fingerprint("0123456789abcdef"));
        assertEquals("0123 45", ScreenText.fingerprint("012345"));
        assertEquals("", ScreenText.fingerprint(""));
        assertEquals("Untrusted catalog Foo, key fingerprint 0123 4567", ScreenText.untrustedLine("Foo", "01234567"));
        assertEquals("Trust catalogs signed with this key?\n\nFoo\n0123 4567\n\n"
                + "Check the fingerprint with the catalog's publisher first.", ScreenText.trustConfirm("Foo", "01234567"));
        assertEquals("This package hasn't been checked for this car.", ScreenText.YELLOW_WARNING);
        assertEquals("This package is marked as not working on this car.", ScreenText.RED_WARNING);
        assertEquals("I understand; install it anyway", ScreenText.RED_TICK);
        assertEquals("Not from a catalog", ScreenText.LOOSE_HEADER);
        assertEquals("Trust this key", ScreenText.TRUST_BUTTON);
    }

    @Test public void keepDriveNote() {
        assertEquals("Don't remove the USB drive until the list finishes.", ScreenText.KEEP_DRIVE);
    }

    @Test public void messages() {
        assertEquals("Remove the other USB drive (/storage/usb1) first: NEMESIS only reads one drive.",
                ScreenText.otherDriveMessage("/storage/usb1"));
        assertEquals("removed x", ScreenText.uninstallResult("removed x\n"));
        assertEquals("please reboot\nRestart the head unit to finish.", ScreenText.uninstallResult("please reboot"));
        assertEquals("", ScreenText.uninstallResult(null));
    }
}
