package hkgpackagemanager.core;

import java.nio.charset.Charset;

/** Where the manager keeps its files on the stick, and the descriptor that reopens the app. */
public final class StickLayout {
    public static final String DESCRIPTOR = "NEMESIS.data";
    public static final String RESUME = "HKGPM/resume.sh";
    public static final String QUEUE = "HKGPM/queue.json";
    public static final String SAVED = "HKGPM/NEMESIS.data.saved";
    public static final String LOG = "HKGPM/log.txt";

    private StickLayout() {}

    public static byte[] resumeDescriptor() {
        return ("[nemesis]\nversion=1.0\ntype=execute\npath=" + RESUME + "\n").getBytes(Charset.forName("UTF-8"));
    }
}
