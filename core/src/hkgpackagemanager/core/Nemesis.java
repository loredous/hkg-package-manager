package hkgpackagemanager.core;

/** The NEMESIS CLI as the rest of the core sees it; the app implements it over /system/xbin/nemesis. */
public interface Nemesis {
    CliResult install(String absZipPath, boolean update);
    CliResult uninstall(String id);
    CliResult pkgList();
    /** Runs a command string through a shell as root (`nemesis root -c`). Quote every argument. */
    CliResult root(String shellCommand);
}
