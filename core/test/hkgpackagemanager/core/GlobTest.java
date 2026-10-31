package hkgpackagemanager.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class GlobTest {
    private static String repeat(String s, int n) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < n; i++) b.append(s);
        return b.toString();
    }

    @Test public void starMatchesRun() {
        assertTrue(Glob.matches("NEEV23.*", "NEEV23.USA.0000.V015.012.260507"));
    }

    @Test public void wholeStringOnly() {
        assertFalse(Glob.matches("NEEV23.*", "xNEEV23.USA"));
        assertFalse(Glob.matches("NEEV23", "NEEV23x"));
    }

    @Test public void caseSensitive() {
        assertFalse(Glob.matches("neev23.*", "NEEV23.USA"));
    }

    @Test public void questionMatchesOne() {
        assertTrue(Glob.matches("NE?V23*", "NEEV23"));
        assertFalse(Glob.matches("?", ""));
    }

    @Test public void emptyAndStar() {
        assertTrue(Glob.matches("*", ""));
        assertTrue(Glob.matches("", ""));
        assertFalse(Glob.matches("", "a"));
    }

    @Test public void multipleStars() {
        assertTrue(Glob.matches("a*b*c", "aXbYbZc"));
        assertFalse(Glob.matches("a*b*c", "aXbYbZ"));
    }

    @Test(timeout = 2000) public void noExponentialBacktracking() {
        assertTrue(Glob.matches(repeat("*a", 30), repeat("a", 30)));
        assertFalse(Glob.matches(repeat("*a", 30), repeat("a", 29) + "b"));
    }
}
