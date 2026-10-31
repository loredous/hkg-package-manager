package hkgpackagemanager.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** The catalog keys the user trusts, as fingerprint to label; text form is "<64 hex> <label>" per line. */
public final class TrustedKeys {
    public static final TrustedKeys NONE = new TrustedKeys(new LinkedHashMap<String, String>());
    private static final int MAX_LABEL = 80;

    private final Map<String, String> keys;

    private TrustedKeys(Map<String, String> keys) {
        this.keys = keys;
    }

    /** Parses the text form; lines that do not start with a 64-digit hex fingerprint are skipped. */
    public static TrustedKeys decode(String text) {
        Map<String, String> m = new LinkedHashMap<String, String>();
        if (text != null) {
            for (String line : text.split("\n")) {
                line = line.trim();
                String fp = line.length() >= 64 ? line.substring(0, 64).toLowerCase() : "";
                if (!isFingerprint(fp)) continue;
                if (line.length() > 64 && line.charAt(64) != ' ') continue;
                m.put(fp, clean(line.substring(64)));
            }
        }
        return new TrustedKeys(m);
    }

    public String encode() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : keys.entrySet()) {
            sb.append(e.getKey());
            if (e.getValue().length() > 0) sb.append(' ').append(e.getValue());
            sb.append('\n');
        }
        return sb.toString();
    }

    public boolean trusts(String fp) {
        return fp != null && keys.containsKey(fp.toLowerCase());
    }

    public TrustedKeys with(String fp, String label) {
        Map<String, String> m = new LinkedHashMap<String, String>(keys);
        m.put(fp.toLowerCase(), clean(label));
        return new TrustedKeys(m);
    }

    public TrustedKeys without(String fp) {
        Map<String, String> m = new LinkedHashMap<String, String>(keys);
        m.remove(fp.toLowerCase());
        return new TrustedKeys(m);
    }

    /** Fingerprint to label, in the order the keys were added. */
    public Map<String, String> labels() {
        return Collections.unmodifiableMap(keys);
    }

    private static boolean isFingerprint(String s) {
        if (s.length() != 64) return false;
        for (int i = 0; i < 64; i++) {
            if (Character.digit(s.charAt(i), 16) < 0) return false;
        }
        return true;
    }

    private static String clean(String label) {
        String s = label == null ? "" : label.replace('\n', ' ').replace('\r', ' ').trim();
        return s.length() > MAX_LABEL ? s.substring(0, MAX_LABEL) : s;
    }
}
