package hkgpackagemanager.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.Test;

public class PackageMetaTest {
    private static void assertReason(String reason, String data) {
        try {
            PackageMeta.from(IniFile.parse(data));
            fail("expected BadPackageException");
        } catch (BadPackageException e) {
            assertEquals(reason, e.getMessage());
        }
    }

    @Test public void defaults() throws Exception {
        PackageMeta m = PackageMeta.from(IniFile.parse("[nemesis]\nid=x.y\n"));
        assertEquals("x.y", m.id);
        assertEquals("x.y", m.name);
        assertEquals("", m.version);
        assertEquals("", m.types);
    }

    @Test public void readsAllFields() throws Exception {
        PackageMeta m = PackageMeta.from(IniFile.parse("[nemesis]\nid=x.y\nname=X Y\nversion=1.2\ntypes=app,script\n"));
        assertEquals("X Y", m.name);
        assertEquals("1.2", m.version);
        assertEquals("app,script", m.types);
    }

    @Test public void missingId() { assertReason("no package id in NEMESIS.data", "[nemesis]\nname=n\n"); }

    @Test public void invalidId() { assertReason("invalid package id: ../x", "[nemesis]\nid=../x\n"); }
}
