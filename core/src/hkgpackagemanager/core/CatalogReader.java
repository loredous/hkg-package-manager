package hkgpackagemanager.core;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Reads the catalog folders under HKGPM/catalogs on the stick; never throws, bad folders become BROKEN rows. */
public final class CatalogReader {
    public static final String DIR = "HKGPM/catalogs";
    private static final int MAX_KEY_BYTES = 16 * 1024;

    /** Size and SHA-256 of a zip; the app passes a cached one. */
    public interface Fingerprinter {
        ZipFingerprint of(File f) throws IOException;
    }

    private CatalogReader() {}

    public static List<CatalogView> read(File stickRoot, TrustedKeys trusted, Fingerprinter fp) {
        List<CatalogView> out = new ArrayList<CatalogView>();
        File[] dirs = new File(stickRoot, DIR).listFiles();
        if (dirs == null) return out;
        Arrays.sort(dirs, new Comparator<File>() {
            @Override public int compare(File a, File b) {
                int c = a.getName().toLowerCase(Locale.ROOT).compareTo(b.getName().toLowerCase(Locale.ROOT));
                return c != 0 ? c : a.getName().compareTo(b.getName());
            }
        });
        for (File dir : dirs) {
            if (dir.getName().startsWith(".") || !dir.isDirectory()) continue;
            String folder = DIR + "/" + dir.getName();
            try {
                out.add(readOne(stickRoot, dir, folder, trusted, fp));
            } catch (RuntimeException e) {
                out.add(broken(folder, "catalog could not be read", null, null));
            }
        }
        return out;
    }

    private static CatalogView broken(String folder, String error, CatalogIndex index, String fingerprint) {
        return new CatalogView(folder, CatalogView.Trust.BROKEN, error, index, fingerprint,
                Collections.<CatalogZip>emptyList());
    }

    private static CatalogView readOne(File stickRoot, File dir, String folder, TrustedKeys trusted,
            Fingerprinter fp) {
        File keyFile = new File(dir, "key.pem");
        File indexFile = new File(dir, "index.json");
        File sigFile = new File(dir, "index.json.sig");
        if (!keyFile.isFile()) return broken(folder, "no key.pem", null, null);
        if (!indexFile.isFile()) return broken(folder, "no index.json", null, null);
        if (!sigFile.isFile()) return broken(folder, "no index.json.sig", null, null);
        if (indexFile.length() > CatalogIndex.MAX_BYTES) return broken(folder, "index.json is too big", null, null);
        byte[] keyBytes;
        byte[] indexBytes;
        byte[] sig;
        try {
            keyBytes = readAll(keyFile, MAX_KEY_BYTES);
            indexBytes = readAll(indexFile, CatalogIndex.MAX_BYTES);
            sig = readAll(sigFile, MAX_KEY_BYTES);
        } catch (IOException e) {
            return broken(folder, "catalog could not be read", null, null);
        }
        if (keyBytes == null || indexBytes == null || sig == null) {
            return broken(folder, keyBytes == null ? "not a public key" : "index.json is too big", null, null);
        }
        CatalogKey key;
        try {
            key = CatalogKey.fromPem(utf8(keyBytes));
        } catch (BadCatalogException e) {
            return broken(folder, e.getMessage(), null, null);
        }
        String fingerprint = key.fingerprint;
        if (!key.verifies(indexBytes, sig)) {
            return broken(folder, "signature doesn't match: the catalog was changed or damaged", null, fingerprint);
        }
        CatalogIndex index;
        try {
            index = CatalogIndex.parse(indexBytes);
        } catch (BadCatalogException e) {
            return broken(folder, e.getMessage(), null, fingerprint);
        }
        if (!fingerprint.equalsIgnoreCase(index.key)) {
            return broken(folder, "index.json names a different key", null, fingerprint);
        }
        if (!dir.getName().equals(index.id)) {
            return broken(folder, "folder name doesn't match catalog id " + index.id, null, fingerprint);
        }
        if (!trusted.trusts(fingerprint)) {
            return new CatalogView(folder, CatalogView.Trust.UNTRUSTED, null, index, fingerprint,
                    Collections.<CatalogZip>emptyList());
        }
        List<CatalogZip> zips = new ArrayList<CatalogZip>();
        for (CatalogEntry e : index.entries) zips.add(zipFor(stickRoot, folder, index, e, fp));
        return new CatalogView(folder, CatalogView.Trust.TRUSTED, null, index, fingerprint, zips);
    }

    private static CatalogZip zipFor(File stickRoot, String folder, CatalogIndex index, CatalogEntry e,
            Fingerprinter fp) {
        String local = e.localPath();
        if (local == null) return new CatalogZip(e, index.name, null, null, e.problem);
        String rel = folder + "/" + local;
        File f = new File(stickRoot, rel);
        if (!f.isFile()) return new CatalogZip(e, index.name, rel, null, CatalogZip.NOT_ON_DRIVE);
        ZipFingerprint z;
        try {
            z = fp.of(f);
        } catch (IOException ex) {
            z = null;
        } catch (RuntimeException ex) {
            z = null;
        }
        StickZip inspected = StickScanner.inspect(f, rel);
        if (z == null || z.size != e.size || !e.sha256.equalsIgnoreCase(z.sha256)) {
            return new CatalogZip(e, index.name, rel, inspected, Messages.MSG_CATALOG_MISMATCH);
        }
        if (inspected.problem != null) return new CatalogZip(e, index.name, rel, inspected, inspected.problem);
        if (!e.id.equals(inspected.meta.id) || !e.version.trim().equals(inspected.meta.version.trim())) {
            return new CatalogZip(e, index.name, rel, inspected, CatalogZip.ZIP_MISMATCH);
        }
        return new CatalogZip(e, index.name, rel, inspected, null);
    }

    /** The file's bytes, or null when it is longer than max. */
    private static byte[] readAll(File f, int max) throws IOException {
        InputStream in = new FileInputStream(f);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > max) return null;
            }
            return out.toByteArray();
        } finally {
            in.close();
        }
    }

    private static String utf8(byte[] b) {
        try {
            return new String(b, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}
