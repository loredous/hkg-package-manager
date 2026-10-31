package hkgpackagemanager.core;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class QueuePreflightTest {
    private static List<String> roots(String... r) { return Arrays.asList(r); }

    @Test public void preflight() {
        assertEquals("/storage/usb1", QueuePreflight.otherDescriptorRoot(roots("/storage/usb0", "/storage/usb1"), "/storage/usb0", r -> r.endsWith("1")));
        assertNull(QueuePreflight.otherDescriptorRoot(roots("/storage/usb0"), "/storage/usb0", r -> true));
    }
}
