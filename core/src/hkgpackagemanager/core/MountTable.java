package hkgpackagemanager.core;

/**
 * Reads /proc/mounts text to tell whether a stick root is really mounted. A pulled stick leaves
 * /storage/usbN behind as an empty folder on the /storage tmpfs, so "the folder exists" is not enough.
 */
public final class MountTable {
    private static final String STORAGE = "/storage/";
    private static final String MEDIA_RW = "/mnt/media_rw/";

    private MountTable() {}

    /** True when some line's mount point equals {@code root} or its /mnt/media_rw twin. */
    public static boolean isMounted(String procMounts, String root) {
        if (procMounts == null || root == null || root.length() == 0) return false;
        String want = stripSlash(root);
        String twin = twin(want);
        for (String line : procMounts.split("\n")) {
            String point = mountPoint(line);
            if (point == null) continue;
            point = stripSlash(point);
            if (point.equals(want) || (twin != null && point.equals(twin))) return true;
        }
        return false;
    }

    /** /storage/usbN for /mnt/media_rw/usbN and the other way round; null for anything else. */
    public static String twin(String root) {
        if (root.startsWith(STORAGE) && root.length() > STORAGE.length()) {
            return MEDIA_RW + root.substring(STORAGE.length());
        }
        if (root.startsWith(MEDIA_RW) && root.length() > MEDIA_RW.length()) {
            return STORAGE + root.substring(MEDIA_RW.length());
        }
        return null;
    }

    /** The second field of a /proc/mounts line, with the kernel's octal escapes (\040 etc.) decoded. */
    static String mountPoint(String line) {
        String[] f = line.trim().split("[ \t]+");
        return f.length < 2 ? null : unescape(f[1]);
    }

    static String unescape(String s) {
        if (s.indexOf('\\') < 0) return s;
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && isOctal(s, i + 1)) {
                b.append((char) Integer.parseInt(s.substring(i + 1, i + 4), 8));
                i += 3;
            } else {
                b.append(c);
            }
        }
        return b.toString();
    }

    private static boolean isOctal(String s, int from) {
        if (from + 3 > s.length()) return false;
        for (int i = from; i < from + 3; i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '7') return false;
        }
        return true;
    }

    private static String stripSlash(String p) {
        while (p.length() > 1 && p.endsWith("/")) p = p.substring(0, p.length() - 1);
        return p;
    }
}
