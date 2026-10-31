package hkgpackagemanager.core;

/** User-visible status texts shared by the queue and the install flow. */
public final class Messages {
    public static final String MSG_INTERRUPTED = "interrupted (head unit restarted or drive removed)";
    public static final String MSG_CHANGED = "changed on the drive";
    public static final String MSG_MISSING = "missing from the drive";
    public static final String MSG_UPDATE_GONE = "was uninstalled; restart the head unit and install it again";
    public static final String MSG_DISCARDED = "not installed (list discarded)";
    public static final String MSG_PATH_TOO_LONG = "path too long for NEMESIS";

    public static final String MSG_CATALOG_MISMATCH = "doesn't match the catalog (size or SHA-256)";
    public static final String MSG_NOT_LISTED = "install failed: NEMESIS does not list it after install";

    private Messages() {}

    public static String installFailed(CliResult r) {
        String last = r.lastLine();
        return "install failed: " + (last != null ? last : "exit " + r.exit);
    }

    /** `installed version is <got>, expected <want>`; a missing or empty version reads "unknown", never "null". */
    public static String versionMismatch(String got, String want) {
        return "installed version is " + shown(got) + ", expected " + shown(want);
    }

    private static String shown(String version) {
        return version == null || version.trim().length() == 0 ? "unknown" : version.trim();
    }
}
