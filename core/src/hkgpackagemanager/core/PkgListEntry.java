package hkgpackagemanager.core;

/** One line of `nemesis pkg list`: id, name, types and whether the package is locked. */
public final class PkgListEntry {
    public final String id;
    public final String name;
    public final String types;
    public final boolean locked;

    public PkgListEntry(String id, String name, String types, boolean locked) {
        this.id = id;
        this.name = name;
        this.types = types;
        this.locked = locked;
    }
}
