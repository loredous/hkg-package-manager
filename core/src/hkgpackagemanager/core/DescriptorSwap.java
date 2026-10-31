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

    public static void end(StickFiles s) throws IOException {
        if (s.exists(StickLayout.DESCRIPTOR) && isOurs(s.read(StickLayout.DESCRIPTOR))) {
            s.delete(StickLayout.DESCRIPTOR);
        }
        // a foreign NEMESIS.data (the user replaced ours) stays, and so does SAVED
        if (s.exists(StickLayout.SAVED) && !s.exists(StickLayout.DESCRIPTOR)) {
            s.move(StickLayout.SAVED, StickLayout.DESCRIPTOR);
        }
        if (s.exists(StickLayout.RESUME)) s.delete(StickLayout.RESUME);
    }

    public static boolean isOurs(byte[] descriptor) {
        if (descriptor == null || descriptor.length == 0) return false;
        IniFile ini = IniFile.parse(new String(descriptor, Charset.forName("UTF-8")));
        return StickLayout.RESUME.equals(ini.get("nemesis", "path"));
    }
}
