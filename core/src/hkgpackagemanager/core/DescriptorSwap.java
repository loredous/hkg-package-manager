package hkgpackagemanager.core;

import java.io.IOException;
import java.nio.charset.Charset;

/** Swaps the stick's NEMESIS.data for our resume descriptor during a multi-install, and back. */
public final class DescriptorSwap {
    private DescriptorSwap() {}

    public static void begin(StickFiles s, byte[] resumeScript) throws IOException {
        if (s.exists(StickLayout.DESCRIPTOR) && !isOurs(s.read(StickLayout.DESCRIPTOR))
                && !s.exists(StickLayout.SAVED)) {
            s.move(StickLayout.DESCRIPTOR, StickLayout.SAVED);
        }
        s.writeAtomic(StickLayout.RESUME, resumeScript);
        s.writeAtomic(StickLayout.DESCRIPTOR, StickLayout.resumeDescriptor());
    }

    /**
     * Removes our descriptor and puts back the stick's original one. Returns true when an original
     * was put back: NEMESIS's handler then sees a new descriptor and may ask about it right away.
     * HKGPM/resume.sh stays on the stick (Ruling R21): a late Yes to NEMESIS's question about our
     * descriptor then runs it harmlessly instead of NEMESIS reporting a missing script.
     */
    public static boolean end(StickFiles s) throws IOException {
        if (s.exists(StickLayout.DESCRIPTOR) && isOurs(s.read(StickLayout.DESCRIPTOR))) {
            s.delete(StickLayout.DESCRIPTOR);
        }
        // a foreign NEMESIS.data (the user replaced ours) stays, and so does SAVED
        if (s.exists(StickLayout.SAVED) && !s.exists(StickLayout.DESCRIPTOR)) {
            s.move(StickLayout.SAVED, StickLayout.DESCRIPTOR);
            return true;
        }
        return false;
    }

    /** True when the stick's root NEMESIS.data is our resume descriptor (unreadable counts as not ours). */
    public static boolean hasOurs(StickFiles s) {
        if (!s.exists(StickLayout.DESCRIPTOR)) return false;
        try {
            return isOurs(s.read(StickLayout.DESCRIPTOR));
        } catch (IOException e) {
            return false;
        }
    }

    public static boolean isOurs(byte[] descriptor) {
        if (descriptor == null || descriptor.length == 0) return false;
        IniFile ini = IniFile.parse(new String(descriptor, Charset.forName("UTF-8")));
        return StickLayout.RESUME.equals(ini.get("nemesis", "path"));
    }
}
