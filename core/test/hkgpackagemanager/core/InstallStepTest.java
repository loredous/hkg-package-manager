package hkgpackagemanager.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Set;
import org.junit.Test;

public class InstallStepTest {
    private final FakeNemesis nem = new FakeNemesis();
    private final InstallStep step = new InstallStep(nem, "/storage/usb0");

    private static QueueItem item(String rel, String id, String ver, boolean update) {
        return new QueueItem(rel, 1, "s", id, ver, update, ItemState.INSTALLING, "");
    }

    private int installCalls() {
        int n = 0;
        for (java.util.List<String> c : nem.calls()) if ("install".equals(c.get(0))) n++;
        return n;
    }

    @Test public void success() {
        nem.scriptInstallSuccess("/storage/usb0/p/a.zip", "a.b", "1.0");
        QueueItem r = step.perform(item("p/a.zip", "a.b", "1.0", false));
        assertEquals(ItemState.DONE, r.state);
        assertEquals("", r.message);
    }

    @Test public void updatePassesFlag() {
        nem.setInstalled("a.b", "1.0");
        nem.scriptInstallSuccess("/storage/usb0/p/a.zip", "a.b", "1.1");
        step.perform(item("p/a.zip", "a.b", "1.1", true));
        assertEquals("--update", nem.calls().get(0).get(1));
    }

    @Test public void versionMismatch() {
        nem.scriptInstallSuccess("/storage/usb0/p/a.zip", "a.b", "1.0");
        QueueItem r = step.perform(item("p/a.zip", "a.b", "1.1", false));
        assertEquals(ItemState.FAILED, r.state);
        assertEquals("installed version is 1.0, expected 1.1", r.message);
    }

    @Test public void versionComparedTrimmed() {
        nem.scriptInstallSuccess("/storage/usb0/p/a.zip", "a.b", "1.0");
        assertEquals(ItemState.DONE, step.perform(item("p/a.zip", "a.b", " 1.0 ", false)).state);
    }

    @Test public void nonZeroExit() {
        nem.scriptInstall("/storage/usb0/p/a.zip", 1, "x\nunzip failed\n");
        QueueItem r = step.perform(item("p/a.zip", "a.b", "1.0", false));
        assertEquals(ItemState.FAILED, r.state);
        assertEquals("install failed: unzip failed", r.message);
    }

    @Test public void updateThatRemovedPackage() {
        nem.scriptInstall("/storage/usb0/p/a.zip", 1, "boom");
        QueueItem r = step.perform(item("p/a.zip", "a.b", "1.0", true));
        assertEquals(Messages.MSG_UPDATE_GONE, r.message);
    }

    @Test public void updateFailedButStillListed() {
        nem.setInstalled("a.b", "0.9");
        nem.scriptInstall("/storage/usb0/p/a.zip", 1, "boom");
        assertEquals("install failed: boom", step.perform(item("p/a.zip", "a.b", "1.0", true)).message);
    }

    @Test public void pathTooLong() {
        StringBuilder b = new StringBuilder("p/");
        for (int i = 0; i < 800; i++) b.append('x');
        QueueItem r = step.perform(item(b.toString(), "a.b", "1.0", false));
        assertEquals(Messages.MSG_PATH_TOO_LONG, r.message);
        assertEquals(ItemState.FAILED, r.state);
        assertEquals(0, installCalls());
    }

    @Test public void installedVersionAndIds() {
        nem.setInstalled("a.b", "2.0");
        nem.setInstalled("c.d", "3.0");
        assertEquals("2.0", step.installedVersion("a.b"));
        assertNull(step.installedVersion("z.z"));
        Set<String> ids = step.installedIds();
        assertEquals(2, ids.size());
        assertTrue(ids.contains("c.d"));
    }

    @Test public void installedVersionWithoutVersionKeyIsEmpty() {
        nem.setInstalled("a.b", null);
        assertEquals("", step.installedVersion("a.b"));
        assertNull(step.installedVersion("z.z")); // cat fails
    }

    @Test public void packageWithoutVersionMatchesInstalledWithoutVersion() {
        nem.scriptInstallSuccess("/storage/usb0/p/a.zip", "a.b", null);
        assertEquals(ItemState.DONE, step.perform(item("p/a.zip", "a.b", "", false)).state);
        nem.scriptInstallSuccess("/storage/usb0/p/b.zip", "b.b", null);
        assertEquals(ItemState.DONE, step.perform(item("p/b.zip", "b.b", null, false)).state);
    }

    @Test public void mismatchNeverSaysNull() {
        nem.scriptInstallSuccess("/storage/usb0/p/a.zip", "a.b", null);
        assertEquals("installed version is unknown, expected 1.0",
                step.perform(item("p/a.zip", "a.b", "1.0", false)).message);
        nem.scriptInstallSuccess("/storage/usb0/p/b.zip", "b.b", "2.0");
        assertEquals("installed version is 2.0, expected unknown",
                step.perform(item("p/b.zip", "b.b", "", false)).message);
        assertEquals("installed version is unknown, expected 1.0", Messages.versionMismatch(null, "1.0"));
    }

    @Test public void exitZeroButNotListed() {
        nem.scriptInstall("/storage/usb0/p/a.zip", 0, "ok installed a.b");
        QueueItem r = step.perform(item("p/a.zip", "a.b", "1.0", false));
        assertEquals(ItemState.FAILED, r.state);
        assertEquals(Messages.MSG_NOT_LISTED, r.message);
    }
}
