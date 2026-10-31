package hkgpackagemanager.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PackageIdTest {
    private static String repeat(char c, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(c);
        return sb.toString();
    }

    @Test public void rules() {
        assertTrue(PackageId.isValid("hkg.climate.page"));
        assertTrue(PackageId.isValid("a_b-c.1"));
        assertFalse(PackageId.isValid(null));
        assertFalse(PackageId.isValid(""));
        assertFalse(PackageId.isValid(".hidden"));
        assertFalse(PackageId.isValid("a..b"));
        assertFalse(PackageId.isValid("a/b"));
        assertFalse(PackageId.isValid("a b"));
        assertFalse(PackageId.isValid(repeat('a', 65)));
        assertTrue(PackageId.isValid(repeat('a', 64)));
    }
}
