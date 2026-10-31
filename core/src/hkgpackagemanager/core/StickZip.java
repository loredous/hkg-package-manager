package hkgpackagemanager.core;

/** One zip found on the stick: either readable package metadata or the reason it cannot be used. */
public final class StickZip {
    public final String relPath;
    public final long size;
    public final PackageMeta meta;
    public final String problem;

    private StickZip(String relPath, long size, PackageMeta meta, String problem) {
        this.relPath = relPath;
        this.size = size;
        this.meta = meta;
        this.problem = problem;
    }

    public static StickZip ok(String relPath, long size, PackageMeta meta) {
        return new StickZip(relPath, size, meta, null);
    }

    public static StickZip problem(String relPath, long size, String problem) {
        return new StickZip(relPath, size, null, problem);
    }
}
