package hkgpackagemanager.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.List;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class CatalogReaderTest {
    private static final String SAMPLE = "tests/catalog/sample";
    private static final String CAT = "HKGPM/catalogs/sample";

    @Rule public TemporaryFolder tmp = new TemporaryFolder();
    private File root;
    private File cat;
    private String fp;

    private static final CatalogReader.Fingerprinter REAL = new CatalogReader.Fingerprinter() {
        @Override public ZipFingerprint of(File f) throws IOException {
            return FileFingerprint.of(f);
        }
    };

    @Before public void copySample() throws Exception {
        root = tmp.newFolder("stick");
        copy(new File(SAMPLE), root);
        cat = new File(root, CAT);
        String idx = new String(read(new File(cat, "index.json")), "UTF-8");
        int i = idx.indexOf("\"key\"");
        int a = idx.indexOf('"', idx.indexOf(':', i) + 1) + 1;
        fp = idx.substring(a, idx.indexOf('"', a));
    }

    private static void copy(File from, File to) throws IOException {
        if (from.isDirectory()) {
            to.mkdirs();
            for (File c : from.listFiles()) copy(c, new File(to, c.getName()));
        } else {
            write(to, read(from));
        }
    }

    private static byte[] read(File f) throws IOException {
        InputStream in = new FileInputStream(f);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        } finally {
            in.close();
        }
    }

    private static void write(File f, byte[] b) throws IOException {
        f.getParentFile().mkdirs();
        FileOutputStream out = new FileOutputStream(f);
        try {
            out.write(b);
        } finally {
            out.close();
        }
    }

    private static void sign(File folder, byte[] index, String keyFile) throws Exception {
        String pem = new String(read(new File("tests/catalog/keys/" + keyFile)), "UTF-8")
                .replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        PrivateKey k = KeyFactory.getInstance("RSA")
                .generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pem)));
        Signature s = Signature.getInstance("SHA256withRSA");
        s.initSign(k);
        s.update(index);
        write(new File(folder, "index.json"), index);
        write(new File(folder, "index.json.sig"), s.sign());
    }

    private List<CatalogView> read() {
        return CatalogReader.read(root, TrustedKeys.NONE.with(fp, "sample"), REAL);
    }

    private CatalogView only() {
        List<CatalogView> v = read();
        assertEquals(1, v.size());
        return v.get(0);
    }

    private CatalogZip zip(CatalogView v, String id) {
        for (CatalogZip z : v.zips) if (z.entry.id.equals(id)) return z;
        throw new AssertionError("no zip " + id);
    }

    @Test public void trustedSampleListsFiveInstallableZips() {
        CatalogView v = only();
        assertEquals(CatalogView.Trust.TRUSTED, v.trust);
        assertEquals(CAT, v.folder);
        assertEquals(fp, v.fingerprint);
        assertEquals(5, v.zips.size());
        for (CatalogZip z : v.zips) {
            assertTrue(z.entry.id + ": " + z.problem, z.installable());
            assertEquals(CAT + "/" + z.entry.localPath(), z.relPath);
            assertEquals(z.relPath, z.zip.relPath);
            assertEquals(v.index.name, z.catalogName);
        }
    }

    @Test public void untrustedKeyGivesUntrustedWithFingerprintAndNoZips() {
        List<CatalogView> l = CatalogReader.read(root, TrustedKeys.NONE, REAL);
        assertEquals(1, l.size());
        assertEquals(CatalogView.Trust.UNTRUSTED, l.get(0).trust);
        assertEquals(fp, l.get(0).fingerprint);
        assertNotNull(l.get(0).index);
        assertTrue(l.get(0).zips.isEmpty());
    }

    @Test public void tamperedIndexIsBroken() throws Exception {
        File i = new File(cat, "index.json");
        write(i, (new String(read(i), "UTF-8") + " ").getBytes("UTF-8"));
        CatalogView v = only();
        assertEquals(CatalogView.Trust.BROKEN, v.trust);
        assertEquals("signature doesn't match: the catalog was changed or damaged", v.error);
        assertNull(v.index);
    }

    @Test public void tamperedZipIsNotInstallable() throws Exception {
        File z = new File(cat, "zips/sample.plain.zip");
        byte[] b = read(z);
        b[b.length / 2] ^= 1;
        write(z, b);
        CatalogZip cz = zip(only(), "sample.plain");
        assertFalse(cz.installable());
        assertEquals(Messages.MSG_CATALOG_MISMATCH, cz.problem);
    }

    @Test public void missingZipIsNotOnDrive() {
        assertTrue(new File(cat, "zips/sample.plain.zip").delete());
        CatalogZip cz = zip(only(), "sample.plain");
        assertFalse(cz.onDrive());
        assertFalse(cz.installable());
        assertEquals(CatalogZip.NOT_ON_DRIVE, cz.problem);
    }

    @Test public void zipWithOtherVersionIsMismatch() throws Exception {
        String idx = new String(read(new File(cat, "index.json")), "UTF-8");
        CatalogIndex parsed = CatalogIndex.parse(idx.getBytes("UTF-8"));
        String ver = null;
        for (CatalogEntry e : parsed.entries) if (e.id.equals("sample.plain")) ver = e.version;
        assertEquals("1.0.0", ver);
        int at = idx.indexOf("\"sample.plain\"");
        int v = idx.indexOf("\"version\"", at);
        int q = idx.indexOf("1.0.0", v);
        String changed = idx.substring(0, q) + "1.0.1" + idx.substring(q + 5);
        sign(cat, changed.getBytes("UTF-8"), "test-key.pem");
        CatalogZip cz = zip(only(), "sample.plain");
        assertFalse(cz.installable());
        assertEquals(CatalogZip.ZIP_MISMATCH, cz.problem);
    }

    @Test public void messyCatalogFolderGivesOneBrokenRowEach() throws Exception {
        File cats = new File(root, "HKGPM/catalogs");
        write(new File(cats, "stray.txt"), new byte[] {1});
        write(new File(cats, ".hidden/key.pem"), new byte[] {1});
        write(new File(cats, "nosig/key.pem"), read(new File(cat, "key.pem")));
        write(new File(cats, "nosig/index.json"), read(new File(cat, "index.json")));
        write(new File(cats, "nokey/index.json"), read(new File(cat, "index.json")));
        copy(cat, new File(cats, "renamed"));
        List<CatalogView> l = read();
        assertEquals(4, l.size());
        assertEquals("nokey", l.get(0).folder.substring(l.get(0).folder.lastIndexOf('/') + 1));
        assertEquals("no key.pem", l.get(0).error);
        assertEquals("no index.json.sig", l.get(1).error);
        assertEquals("folder name doesn't match catalog id sample", l.get(2).error);
        assertEquals(CatalogView.Trust.BROKEN, l.get(2).trust);
        assertEquals(CatalogView.Trust.TRUSTED, l.get(3).trust);
    }

    @Test public void oversizedIndexIsBroken() throws Exception {
        write(new File(cat, "index.json"), new byte[CatalogIndex.MAX_BYTES + 1]);
        CatalogView v = only();
        assertEquals(CatalogView.Trust.BROKEN, v.trust);
        assertEquals("index.json is too big", v.error);
    }

    @Test public void noCatalogFolderGivesEmptyList() throws Exception {
        File empty = tmp.newFolder("empty");
        assertTrue(CatalogReader.read(empty, TrustedKeys.NONE, REAL).isEmpty());
    }

    @Test public void signatureCheckedBeforeParsing() throws Exception {
        write(new File(cat, "index.json"), "not json at all".getBytes("UTF-8"));
        write(new File(cat, "index.json.sig"), new byte[] {1, 2, 3});
        assertEquals("signature doesn't match: the catalog was changed or damaged", only().error);
    }

    @Test public void indexNamingOtherKeyIsBroken() throws Exception {
        String idx = new String(read(new File(cat, "index.json")), "UTF-8").replace(fp, "0" + fp.substring(1));
        sign(cat, idx.getBytes("UTF-8"), "test-key.pem");
        assertEquals("index.json names a different key", only().error);
    }
}
