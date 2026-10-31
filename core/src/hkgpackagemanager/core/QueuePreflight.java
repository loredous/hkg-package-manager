package hkgpackagemanager.core;

import java.util.List;

/** Checks made before a multi-install starts. */
public final class QueuePreflight {
    /** Whether a root has a NEMESIS.data (our own interface: API 19 has no java.util.function). */
    public interface Exists { boolean at(String root); }

    private QueuePreflight() {}

    /** The first mounted root other than the chosen one that has a NEMESIS.data, or null. */
    public static String otherDescriptorRoot(List<String> mountedRoots, String chosenRoot, Exists descriptorExists) {
        for (String r : mountedRoots) {
            if (!r.equals(chosenRoot) && descriptorExists.at(r)) return r;
        }
        return null;
    }
}
