package hkgpackagemanager.core;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

public class StatusLabelerTest {
    private static StickZip zip(String version) throws BadPackageException {
        return StickZip.ok("a.zip", 1, PackageMeta.from(IniFile.parse("[nemesis]\nid=x.y\nversion=" + version + "\n")));
    }

    private static Map<String, InstalledPackage> installed(String version) {
        Map<String, InstalledPackage> m = new HashMap<String, InstalledPackage>();
        m.put("x.y", new InstalledPackage(new PkgListEntry("x.y", "X", "", false), version));
        return m;
    }

    @Test public void labels() throws Exception {
        assertEquals(ZipStatus.NEW, StatusLabeler.label(zip("2.0.0"), new HashMap<String, InstalledPackage>()));
        assertEquals(ZipStatus.SAME, StatusLabeler.label(zip("2.0.0"), installed("2.0.0")));
        assertEquals(ZipStatus.UPDATE, StatusLabeler.label(zip("2.0.1"), installed(" 2.0.0")));
        assertEquals(ZipStatus.UPDATE, StatusLabeler.label(zip("2.0.0"), installed(null)));
        assertEquals(ZipStatus.INVALID, StatusLabeler.label(StickZip.problem("a.zip", 1, "not a readable zip"),
                new HashMap<String, InstalledPackage>()));
    }
}
