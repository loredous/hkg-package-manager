package hkgpackagemanager.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Computes the ZipFingerprint of a file. */
public final class FileFingerprint {
    private FileFingerprint() {}

    /** Size and SHA-256 of the file, or null when it is not a file. */
    public static ZipFingerprint of(File f) throws IOException {
        if (!f.isFile()) return null;
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("no SHA-256");
        }
        long size = 0;
        InputStream in = new FileInputStream(f);
        try {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                md.update(buf, 0, n);
                size += n;
            }
        } finally {
            in.close();
        }
        return new ZipFingerprint(size, Hex.of(md.digest()));
    }
}
