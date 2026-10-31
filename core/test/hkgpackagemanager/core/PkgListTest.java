package hkgpackagemanager.core;

import static org.junit.Assert.*;

import java.util.List;
import org.junit.Test;

public class PkgListTest {
    @Test public void parsesLinesAndSkipsJunk() {
        List<PkgListEntry> l = PkgList.parse("usb-handler|USB Handler|1|service\n\nhkg.climate.page|HKG Climate Page|0|\ngarbage\n");
        assertEquals(2, l.size());
        assertTrue(l.get(0).locked);
        assertEquals("usb-handler", l.get(0).id);
        assertEquals("USB Handler", l.get(0).name);
        assertEquals("service", l.get(0).types);
        assertFalse(l.get(1).locked);
        assertEquals("", l.get(1).types);
    }

    @Test public void handlesCrLfAndNull() {
        assertEquals(1, PkgList.parse("a.b|A|0|x\r\n").size());
        assertEquals("x", PkgList.parse("a.b|A|0|x\r\n").get(0).types);
        assertEquals(0, PkgList.parse(null).size());
    }

    @Test public void pipeInNameKeepsFieldsAligned() {
        PkgListEntry e = PkgList.parse("x.y|A|B|1|service\n").get(0);
        assertEquals("A|B", e.name);
        assertTrue(e.locked);
        assertEquals("service", e.types);
    }
}
