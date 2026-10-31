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
        DescriptorSwap.end(s);
        assertEquals(ORIG, s.text("NEMESIS.data"));
        assertFalse(s.exists("HKGPM/NEMESIS.data.saved"));
    }
    @Test public void endWithoutOriginalRemovesOurs() throws Exception {
        MemStickFiles s = new MemStickFiles();
        DescriptorSwap.begin(s, bytes("x"));
        assertTrue(s.exists("NEMESIS.data"));
        DescriptorSwap.end(s);
        assertFalse(s.exists("NEMESIS.data"));
    }
    @Test public void endLeavesForeignDescriptorAlone() throws Exception {
        MemStickFiles s = new MemStickFiles(); s.put("NEMESIS.data", ORIG);
        DescriptorSwap.begin(s, bytes("x"));
        s.put("NEMESIS.data", "[nemesis]\ntype=execute\npath=other.sh\n");
        DescriptorSwap.end(s);
        assertEquals("[nemesis]\ntype=execute\npath=other.sh\n", s.text("NEMESIS.data"));
        assertEquals(ORIG, s.text("HKGPM/NEMESIS.data.saved"));
    }
    @Test public void isOurs() {
        assertTrue(DescriptorSwap.isOurs(StickLayout.resumeDescriptor()));
        assertFalse(DescriptorSwap.isOurs(bytes(ORIG)));
        assertFalse(DescriptorSwap.isOurs(bytes("")));
        assertFalse(DescriptorSwap.isOurs(bytes("garbage")));
    }
    @Test public void failedWriteSurfaces() throws Exception {
        MemStickFiles s = new MemStickFiles(); s.put("NEMESIS.data", ORIG);
        s.failWritesAfter(1);
        try { DescriptorSwap.begin(s, bytes("x")); fail(); } catch (java.io.IOException e) { assertEquals("stick removed", e.getMessage()); }
    }
}
