package hkgpackagemanager.core;

/** The [nemesis] header of a NEMESIS.data: id (required and valid), name, version and types. */
public final class PackageMeta {
    public final String id;
    public final String name;
    public final String version;
    public final String types;

    private PackageMeta(String id, String name, String version, String types) {
        this.id = id;
        this.name = name;
        this.version = version;
        this.types = types;
    }

    public static PackageMeta from(IniFile ini) throws BadPackageException {
        String id = ini.get("nemesis", "id");
        if (id == null || id.length() == 0) throw new BadPackageException("no package id in NEMESIS.data");
        if (!PackageId.isValid(id)) throw new BadPackageException("invalid package id: " + id);
        return new PackageMeta(id, orDefault(ini.get("nemesis", "name"), id),
                orDefault(ini.get("nemesis", "version"), ""), orDefault(ini.get("nemesis", "types"), ""));
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.length() == 0 ? fallback : value;
    }
}
