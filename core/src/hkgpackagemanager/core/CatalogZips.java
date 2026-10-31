package hkgpackagemanager.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The installable zips of trusted catalogs, for the queue: as scan results and as expected fingerprints. */
public final class CatalogZips {
    private CatalogZips() {}

    public static List<StickZip> zips(List<CatalogView> views) {
        List<StickZip> out = new ArrayList<StickZip>();
        for (CatalogView v : views) {
            if (v.trust != CatalogView.Trust.TRUSTED) continue;
            for (CatalogZip z : v.zips) {
                if (z.installable()) out.add(z.zip);
            }
        }
        return out;
    }

    /** Expected size and SHA-256 keyed by stick-relative path. */
    public static Map<String, ZipFingerprint> expected(List<CatalogView> views) {
        Map<String, ZipFingerprint> out = new HashMap<String, ZipFingerprint>();
        for (CatalogView v : views) {
            if (v.trust != CatalogView.Trust.TRUSTED) continue;
            for (CatalogZip z : v.zips) {
                if (z.installable()) out.put(z.zip.relPath, new ZipFingerprint(z.entry.size, z.entry.sha256));
            }
        }
        return out;
    }
}
