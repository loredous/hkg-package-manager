package hkgpackagemanager.core;

/** The package id rules: [A-Za-z0-9._-], 1-64 characters, no leading '.', no "..". */
public final class PackageId {
    private static final int MAX_LENGTH = 64;

    private PackageId() {}

    public static boolean isValid(String id) {
        if (id == null || id.length() == 0 || id.length() > MAX_LENGTH) return false;
        if (id.charAt(0) == '.' || id.indexOf("..") >= 0) return false;
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '.' || c == '_' || c == '-';
            if (!ok) return false;
        }
        return true;
    }
}
