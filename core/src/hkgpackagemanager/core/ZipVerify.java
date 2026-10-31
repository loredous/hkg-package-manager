package hkgpackagemanager.core;

import java.io.IOException;

/** Checks a zip on the stick against the size and SHA-256 it was queued with. */
public final class ZipVerify {
    private ZipVerify() {}

    /** MSG_MISSING when the zip is gone, MSG_CATALOG_MISMATCH when a catalog hash is set and differs, else null. */
    public static String problem(ZipCheck check, String relPath, long size, String sha256) throws IOException {
        ZipFingerprint f = check.fingerprint(relPath);
        if (f == null) return Messages.MSG_MISSING;
        return differs(f, size, sha256) ? Messages.MSG_CATALOG_MISMATCH : null;
    }

    /** True when sha256 is set (a catalog zip) and the fingerprint differs from it in size or hash. */
    static boolean differs(ZipFingerprint f, long size, String sha256) {
        return !sha256.isEmpty() && (f.size != size || !f.sha256.equalsIgnoreCase(sha256));
    }
}
