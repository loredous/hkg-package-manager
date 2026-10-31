package hkgpackagemanager.app;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Finds the mounted USB sticks. */
public final class Sticks {
    /** The only places a stick can be mounted. */
    public static final String[] ROOTS = {"/storage/usb0", "/storage/usb1", "/storage/usb2"};

    private Sticks() {}

    public static boolean isStickRoot(String path) {
        for (String r : ROOTS) if (r.equals(path)) return true;
        return false;
    }

    /** /storage/usb0..2 that are readable directories. */
    public static List<String> mounted() {
        List<String> found = new ArrayList<String>();
        for (String path : ROOTS) {
            File f = new File(path);
            if (f.isDirectory() && f.list() != null) found.add(path);
        }
        return found;
    }
}
