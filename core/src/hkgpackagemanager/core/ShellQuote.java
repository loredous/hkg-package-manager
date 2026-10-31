package hkgpackagemanager.core;

/** POSIX single-quote escaping for the one place a shell is involved (`nemesis root -c`). */
public final class ShellQuote {
    private ShellQuote() {}

    /** Wraps s in single quotes, escaping embedded quotes. Refuses newline, CR and NUL. */
    public static String quote(String s) {
        if (s == null) throw new IllegalArgumentException("null");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\n' || c == '\r' || c == '\0') throw new IllegalArgumentException("control character in shell argument");
        }
        return "'" + s.replace("'", "'\\''") + "'";
    }
}
