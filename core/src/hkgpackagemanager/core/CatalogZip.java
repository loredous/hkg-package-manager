package hkgpackagemanager.core;

/** One catalog entry matched against the stick: the zip as found there, or why it cannot be installed. */
public final class CatalogZip {
    public static final String NOT_ON_DRIVE = "not on this drive";
    public static final String ZIP_MISMATCH = "zip doesn't match its catalog entry (id or version)";

    public final CatalogEntry entry;
    public final String catalogName;
    public final String relPath;
    public final StickZip zip;
    public final String problem;

    public CatalogZip(CatalogEntry entry, String catalogName, String relPath, StickZip zip, String problem) {
        this.entry = entry;
        this.catalogName = catalogName;
        this.relPath = relPath;
        this.zip = zip;
        this.problem = problem;
    }

    public boolean onDrive() {
        return zip != null;
    }

    public boolean installable() {
        return onDrive() && problem == null;
    }
}
