package hkgpackagemanager.core;

import java.util.Map;

/** Compares a stick zip with what is installed. Versions are compared as trimmed strings, equality only. */
public final class StatusLabeler {
    private StatusLabeler() {}

    public static ZipStatus label(StickZip zip, Map<String, InstalledPackage> installedById) {
        if (zip.meta == null) return ZipStatus.INVALID;
        InstalledPackage inst = installedById.get(zip.meta.id);
        if (inst == null) return ZipStatus.NEW;
        if (inst.version == null) return ZipStatus.UPDATE;
        return inst.version.trim().equals(zip.meta.version.trim()) ? ZipStatus.SAME : ZipStatus.UPDATE;
    }
}
