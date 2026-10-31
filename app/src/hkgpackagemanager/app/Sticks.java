package hkgpackagemanager.app;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Finds the mounted USB sticks. */
public final class Sticks {
    private Sticks() {}

    /** /storage/usb0..2 that are readable directories. */
    public static List<String> mounted() {
        List<String> found = new ArrayList<String>();
        for (int i = 0; i <= 2; i++) {
            String path = "/storage/usb" + i;
            File f = new File(path);
            if (f.isDirectory() && f.list() != null) found.add(path);
        }
        return found;
    }
}
