package hkgpackagemanager.core;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Builds argv lists and root shell commands for the NEMESIS CLI. Pure; nothing here runs anything. */
public final class NemesisCalls {
    /** Per-argument limit of the NEMESIS daemon (SU_MAX_ARG). */
    public static final int MAX_ARG_BYTES = 768;
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private NemesisCalls() {}

    public static List<String> install(String zip, boolean update) {
        List<String> a = new ArrayList<String>();
        a.add("install");
        if (update) a.add("--update");
        a.add(zip);
        return a;
    }

    public static List<String> uninstall(String id) { return Arrays.asList("uninstall", id); }

    public static List<String> pkgList() { return Arrays.asList("pkg-list"); }

    public static List<String> root(String cmd) { return Arrays.asList("root", "-c", cmd); }

    public static String catCommand(String path) { return "cat " + ShellQuote.quote(path); }

    /** Copies src to dst atomically (via dst.tmp), creating dst's directory, then syncs. */
    public static String copyIntoCommand(String src, String dst) {
        String dir = dst.substring(0, Math.max(dst.lastIndexOf('/'), 0));
        String tmp = dst + ".tmp";
        return "mkdir -p " + ShellQuote.quote(dir) + " && cp " + ShellQuote.quote(src) + " " + ShellQuote.quote(tmp)
                + " && mv " + ShellQuote.quote(tmp) + " " + ShellQuote.quote(dst) + " && sync";
    }

    public static boolean fitsDaemon(List<String> argv) {
        for (String s : argv) if (s.getBytes(UTF8).length > MAX_ARG_BYTES) return false;
        return true;
    }

    public static String installedMetaPath(String id) {
        if (!PackageId.isValid(id)) throw new IllegalArgumentException("invalid package id: " + id);
        return "/data/nemesis/apps/" + id + "/NEMESIS.data";
    }
}
