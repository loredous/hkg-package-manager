package hkgpackagemanager.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MountTableTest {
    private static final String BASE = "rootfs / rootfs ro,relatime 0 0\n"
            + "tmpfs /storage tmpfs rw,relatime,mode=050,gid=1028 0 0\n"
            + "/dev/fuse /storage/emulated/legacy fuse rw,nosuid,nodev 0 0\n";

    @Test public void storageMountPoint() {
        String t = BASE + "/dev/fuse /storage/usb0 fuse rw,nosuid,nodev,relatime 0 0\n";
        assertTrue(MountTable.isMounted(t, "/storage/usb0"));
        assertFalse(MountTable.isMounted(t, "/storage/usb1"));
    }

    @Test public void mediaRwTwinCounts() {
        String t = BASE + "/dev/block/vold/8:1 /mnt/media_rw/usb1 vfat rw,dirsync,nosuid 0 0\n";
        assertTrue(MountTable.isMounted(t, "/storage/usb1"));
        assertFalse(MountTable.isMounted(t, "/storage/usb0"));
    }

    @Test public void pulledStickLeavesOnlyTheTmpfs() {
        // the empty /storage/usb0 folder lives on the /storage tmpfs; that is not a mount of usb0
        assertFalse(MountTable.isMounted(BASE, "/storage/usb0"));
        assertFalse(MountTable.isMounted(BASE + "/dev/fuse /storage/usb00 fuse rw 0 0\n", "/storage/usb0"));
    }

    @Test public void octalEscapesAreDecoded() {
        String t = BASE + "/dev/sda1 /mnt/my\\040stick vfat rw 0 0\n"
                + "/dev/sdb1 /storage/usb\\0602 vfat rw 0 0\n"; // \060 is '0', so this is /storage/usb02
        assertTrue(MountTable.isMounted(t, "/mnt/my stick"));
        assertTrue(MountTable.isMounted(t, "/storage/usb02"));
        assertEquals("/a b\tc\\d", MountTable.unescape("/a\\040b\\011c\\134d"));
        assertEquals("/a\\x", MountTable.unescape("/a\\x"));
        assertEquals("/a\\04", MountTable.unescape("/a\\04"));
    }

    @Test public void oddInput() {
        assertFalse(MountTable.isMounted(null, "/storage/usb0"));
        assertFalse(MountTable.isMounted(BASE, null));
        assertFalse(MountTable.isMounted("\n\ngarbage\n", "/storage/usb0"));
        assertTrue(MountTable.isMounted("/dev/fuse /storage/usb0/ fuse rw 0 0", "/storage/usb0/"));
        assertTrue(MountTable.isMounted("/dev/fuse\t/storage/usb2 fuse rw 0 0\r\n", "/storage/usb2"));
    }

    @Test public void twins() {
        assertEquals("/mnt/media_rw/usb0", MountTable.twin("/storage/usb0"));
        assertEquals("/storage/usb2", MountTable.twin("/mnt/media_rw/usb2"));
        assertNull(MountTable.twin("/storage/"));
        assertNull(MountTable.twin("/data"));
    }
}
