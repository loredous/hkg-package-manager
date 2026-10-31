package hkgpackagemanager.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class ZipVerifyTest {
    private static ZipCheck returning(final ZipFingerprint f) {
        return new ZipCheck() {
            public ZipFingerprint fingerprint(String relPath) { return f; }
        };
    }

    @Test public void zipVerifyCatchesSwap() throws Exception {
        assertEquals(Messages.MSG_CATALOG_MISMATCH,
                ZipVerify.problem(returning(new ZipFingerprint(5, "B")), "a.zip", 5, "A"));
        assertEquals(Messages.MSG_CATALOG_MISMATCH,
                ZipVerify.problem(returning(new ZipFingerprint(6, "A")), "a.zip", 5, "A"));
        assertNull(ZipVerify.problem(returning(new ZipFingerprint(5, "A")), "a.zip", 5, "A"));
    }

    @Test public void zipVerifyMissing() throws Exception {
        assertEquals(Messages.MSG_MISSING, ZipVerify.problem(returning(null), "a.zip", 5, "A"));
    }

    @Test public void zipVerifyLooseIsNull() throws Exception {
        assertNull(ZipVerify.problem(returning(new ZipFingerprint(9, "X")), "a.zip", 0, ""));
    }
}
