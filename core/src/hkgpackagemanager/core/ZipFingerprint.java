package hkgpackagemanager.core;

/** Size and SHA-256 of a zip on the stick, used to notice that it changed after it was queued. */
public final class ZipFingerprint {
    public final long size;
    public final String sha256;

    public ZipFingerprint(long size, String sha256) {
        this.size = size;
        this.sha256 = sha256;
    }
}
