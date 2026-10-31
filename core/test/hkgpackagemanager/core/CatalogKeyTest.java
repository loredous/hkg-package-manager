package hkgpackagemanager.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import org.json.JSONObject;
import org.junit.Test;

public class CatalogKeyTest {
    private static final String DIR = "tests/catalog/sample/HKGPM/catalogs/sample/";

    private static byte[] read(String name) throws Exception {
        InputStream in = new FileInputStream(DIR + name);
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

    private static String pem(String label, byte[] der) {
        return "-----BEGIN " + label + "-----\n"
                + java.util.Base64.getMimeEncoder(64, new byte[] {'\n'}).encodeToString(der)
                + "\n-----END " + label + "-----\n";
    }

    private static KeyPair gen(String alg, int bits) throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance(alg);
        g.initialize(bits);
        return g.generateKeyPair();
    }

    private static CatalogKey sample() throws Exception {
        return CatalogKey.fromPem(new String(read("key.pem"), "UTF-8"));
    }

    @Test public void sampleKeyVerifiesSampleIndex() throws Exception {
        assertTrue(sample().verifies(read("index.json"), read("index.json.sig")));
    }

    @Test public void fingerprintIsSha256OfSpki() throws Exception {
        String key = new JSONObject(new String(read("index.json"), "UTF-8")).getString("key");
        assertEquals(key, sample().fingerprint);
        assertEquals(Hex.sha256(sample().spki), sample().fingerprint);
    }

    @Test public void oneChangedByteFails() throws Exception {
        byte[] data = read("index.json");
        data[data.length / 2] ^= 1;
        assertFalse(sample().verifies(data, read("index.json.sig")));
    }

    @Test public void otherKeyFails() throws Exception {
        KeyPair kp = gen("RSA", 2048);
        CatalogKey other = CatalogKey.fromPem(pem("PUBLIC KEY", kp.getPublic().getEncoded()));
        assertFalse(other.verifies(read("index.json"), read("index.json.sig")));
    }

    @Test(expected = BadCatalogException.class) public void privateKeyPemRejected() throws Exception {
        CatalogKey.fromPem(pem("PRIVATE KEY", gen("RSA", 2048).getPrivate().getEncoded()));
    }

    @Test public void shortKeyRejected() throws Exception {
        try {
            CatalogKey.fromPem(pem("PUBLIC KEY", gen("RSA", 1024).getPublic().getEncoded()));
            org.junit.Assert.fail();
        } catch (BadCatalogException e) {
            assertEquals("key too short", e.getMessage());
        }
    }

    @Test public void ecKeyRejected() throws Exception {
        try {
            CatalogKey.fromPem(pem("PUBLIC KEY", gen("EC", 256).getPublic().getEncoded()));
            org.junit.Assert.fail();
        } catch (BadCatalogException e) {
            assertEquals("not an RSA key", e.getMessage());
        }
    }

    @Test public void garbageSignatureIsFalseNotThrown() throws Exception {
        assertFalse(sample().verifies(read("index.json"), new byte[] {1, 2, 3}));
        assertFalse(sample().verifies(read("index.json"), new byte[0]));
        assertFalse(sample().verifies(null, null));
    }
}
