package hkgpackagemanager.core;

/** Flat file name for staging a stick-relative path in the app's private folder. */
public final class StageName {
    private StageName() {}

    /** Replaces every character outside [A-Za-z0-9._-] (including "/") with "_" so rel maps to one safe file name. */
    public static String flatten(String rel) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < rel.length(); i++) {
            char c = rel.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                || c == '.' || c == '-' || c == '_';
            sb.append(ok ? c : '_');
        }
        String s = sb.toString();
        return s.length() == 0 || s.equals(".") || s.equals("..") ? "_" + s : s;
    }
}
