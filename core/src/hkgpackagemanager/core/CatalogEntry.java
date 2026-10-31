package hkgpackagemanager.core;

/** One package listed in a catalog index; problem is non-null when the entry cannot be used. */
public final class CatalogEntry {
    public final String id;
    public final String name;
    public final String version;
    public final String description;
    public final String author;
    public final String homepage;
    public final String zip;
    public final long size;
    public final String sha256;
    public final Compat compat;
    public final String problem;

    public CatalogEntry(String id, String name, String version, String description, String author,
            String homepage, String zip, long size, String sha256, Compat compat, String problem) {
        this.id = id;
        this.name = name;
        this.version = version;
        this.description = description;
        this.author = author;
        this.homepage = homepage;
        this.zip = zip;
        this.size = size;
        this.sha256 = sha256;
        this.compat = compat == null ? Compat.NONE : compat;
        this.problem = problem;
    }

    /** An unusable entry; id and name are kept for display when they could be read. */
    static CatalogEntry invalid(String id, String name, String problem) {
        return new CatalogEntry(id, name, null, null, null, null, null, 0, null, Compat.NONE, problem);
    }

    /** True when the zip is fetched from an http(s) URL (a stick holds it under remote/). */
    public boolean isRemote() {
        return problem == null && isUrl(zip);
    }

    static boolean isUrl(String z) {
        return z != null && (z.startsWith("http://") || z.startsWith("https://"));
    }

    /** The zip's path relative to the catalog folder, or null when the entry is invalid. */
    public String localPath() {
        if (problem != null) return null;
        return isUrl(zip) ? "remote/" + id + ".zip" : zip;
    }
}
