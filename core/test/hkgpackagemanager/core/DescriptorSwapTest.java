package hkgpackagemanager.core;

import static org.junit.Assert.*;

import java.nio.charset.Charset;
import org.junit.Test;

public class DescriptorSwapTest {
    private static final String ORIG = "[nemesis]\ntype=install\npath=packages/a.zip\n";
    private static byte[] bytes(String s) { return s.getBytes(Charset.forName("UTF-8")); }

    @Test public void beginSavesForeignDescriptorAndWritesOurs() throws Exception {
        MemStickFiles s = new MemStickFiles(); s.put("NEMESIS.data", ORIG);
        DescriptorSwap.begin(s, bytes("#!/system/bin/sh\n"));
        assertEquals(ORIG, s.text("HKGPM/NEMESIS.data.saved"));
        assertArrayEquals(StickLayout.resumeDescriptor(), s.read("NEMESIS.data"));
        assertEquals("#!/system/bin/sh\n", s.text("HKGPM/resume.sh"));
    }
    @Test public void beginTwiceKeepsOriginalSaved() throws Exception {
        MemStickFiles s = new MemStickFiles(); s.put("NEMESIS.data", ORIG);
        DescriptorSwap.begin(s, bytes("x"));
        DescriptorSwap.begin(s, bytes("x"));
        assertEquals(ORIG, s.text("HKGPM/NEMESIS.data.saved"));
    }
    @Test public void endRestoresOriginal() throws Exception {
        MemStickFiles s = new MemStickFiles(); s.put("NEMESIS.data", ORIG);
        DescriptorSwap.begin(s, bytes("x"));
        assertTrue(DescriptorSwap.end(s));
        assertEquals(ORIG, s.text("NEMESIS.data"));
        assertFalse(s.exists("HKGPM/NEMESIS.data.saved"));
        assertEquals("x", s.text("HKGPM/resume.sh")); // kept for a late Yes (R21)
        assertFalse(DescriptorSwap.end(s)); // nothing left to put back
        assertEquals(ORIG, s.text("NEMESIS.data"));
    }
    @Test public void endWithoutOriginalRemovesOurs() throws Exception {
        MemStickFiles s = new MemStickFiles();
        DescriptorSwap.begin(s, bytes("x"));
        assertTrue(s.exists("NEMESIS.data"));
        assertFalse(DescriptorSwap.end(s));
        assertFalse(s.exists("NEMESIS.data"));
        assertTrue(s.exists("HKGPM/resume.sh"));
    }
    @Test public void endLeavesForeignDescriptorAlone() throws Exception {
        MemStickFiles s = new MemStickFiles(); s.put("NEMESIS.data", ORIG);
        DescriptorSwap.begin(s, bytes("x"));
        s.put("NEMESIS.data", "[nemesis]\ntype=execute\npath=other.sh\n");
        assertFalse(DescriptorSwap.end(s));
        assertEquals("[nemesis]\ntype=execute\npath=other.sh\n", s.text("NEMESIS.data"));
        assertEquals(ORIG, s.text("HKGPM/NEMESIS.data.saved"));
    }
    @Test public void isOurs() {
        assertTrue(DescriptorSwap.isOurs(StickLayout.resumeDescriptor()));
        assertFalse(DescriptorSwap.isOurs(bytes(ORIG)));
        assertFalse(DescriptorSwap.isOurs(bytes("")));
        assertFalse(DescriptorSwap.isOurs(bytes("garbage")));
    }
    @Test public void hasOurs() throws Exception {
        MemStickFiles s = new MemStickFiles();
        assertFalse(DescriptorSwap.hasOurs(s));
        s.put("NEMESIS.data", ORIG);
        assertFalse(DescriptorSwap.hasOurs(s));
        DescriptorSwap.begin(s, bytes("x"));
        assertTrue(DescriptorSwap.hasOurs(s));
    }
    @Test public void failedWriteSurfaces() throws Exception {
        MemStickFiles s = new MemStickFiles(); s.put("NEMESIS.data", ORIG);
        s.failWritesAfter(1);
        try { DescriptorSwap.begin(s, bytes("x")); fail(); } catch (java.io.IOException e) { assertEquals("stick removed", e.getMessage()); }
    }
}
