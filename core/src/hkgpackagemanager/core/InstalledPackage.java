package hkgpackagemanager.core;

/** An installed package and its version (null when the version could not be read). */
public final class InstalledPackage {
    public final PkgListEntry entry;
    public final String version;

    public InstalledPackage(PkgListEntry entry, String version) {
        this.entry = entry;
        this.version = version;
    }
}
