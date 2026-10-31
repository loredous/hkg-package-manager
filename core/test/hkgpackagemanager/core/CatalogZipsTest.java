package hkgpackagemanager.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class CatalogZipsTest {
    private static CatalogZip cz(String rel, long size, String sha, boolean installable) throws Exception {
        PackageMeta m = PackageMeta.from(IniFile.parse("[nemesis]\nid=hkg.x\nversion=1\n"));
        CatalogEntry e = new CatalogEntry("hkg.x", "X", "1", null, null, null, rel, size, sha, Compat.NONE, null);
        return new CatalogZip(e, "cat", rel, StickZip.ok(rel, size, m), installable ? null : CatalogZip.ZIP_MISMATCH);
    }

    private static CatalogView view(CatalogView.Trust t, CatalogZip... zips) {
        return new CatalogView("cat", t, null, null, "fp", new ArrayList<CatalogZip>(Arrays.asList(zips)));
    }

    @Test public void onlyInstallableZipsOfTrustedCatalogs() throws Exception {
        CatalogZip good = cz("cat/a.zip", 7, "aa", true);
        CatalogZip bad = cz("cat/b.zip", 8, "bb", false);
        CatalogZip untrusted = cz("u/c.zip", 9, "cc", true);
        List<CatalogView> views = Arrays.asList(view(CatalogView.Trust.TRUSTED, good, bad),
                view(CatalogView.Trust.UNTRUSTED, untrusted), view(CatalogView.Trust.BROKEN));
        List<StickZip> zips = CatalogZips.zips(views);
        assertEquals(1, zips.size());
        assertSame(good.zip, zips.get(0));
        Map<String, ZipFingerprint> exp = CatalogZips.expected(views);
        assertEquals(1, exp.size());
        assertEquals(7, exp.get("cat/a.zip").size);
        assertEquals("aa", exp.get("cat/a.zip").sha256);
        assertNull(exp.get("cat/b.zip"));
        assertNull(exp.get("u/c.zip"));
    }
}
