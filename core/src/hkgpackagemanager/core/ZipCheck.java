package hkgpackagemanager.core;

import java.io.IOException;

/** Fingerprints a zip on the stick by its path relative to the stick root. */
public interface ZipCheck {
    /** The fingerprint, or null when the file is missing. */
    ZipFingerprint fingerprint(String relPath) throws IOException;
}
