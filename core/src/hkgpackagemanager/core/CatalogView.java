package hkgpackagemanager.core;

import java.util.Collections;
import java.util.List;

/** One catalog folder on the stick: trusted, untrusted (name only) or broken (with the reason). */
public final class CatalogView {
    public enum Trust { TRUSTED, UNTRUSTED, BROKEN }

    public final String folder;
    public final Trust trust;
    public final String error;
    public final CatalogIndex index;
    public final String fingerprint;
    public final List<CatalogZip> zips;

    public CatalogView(String folder, Trust trust, String error, CatalogIndex index, String fingerprint,
            List<CatalogZip> zips) {
        this.folder = folder;
        this.trust = trust;
        this.error = error;
        this.index = index;
        this.fingerprint = fingerprint;
        this.zips = Collections.unmodifiableList(zips);
    }
}
