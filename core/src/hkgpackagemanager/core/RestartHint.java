package hkgpackagemanager.core;

import java.util.regex.Pattern;

/** Detects NEMESIS output asking for a restart. */
public final class RestartHint {
    private static final Pattern P = Pattern.compile("(?i)\\b(reboot|restart)");

    private RestartHint() {}

    public static boolean mentionsRestart(String output) {
        return output != null && P.matcher(output).find();
    }
}
