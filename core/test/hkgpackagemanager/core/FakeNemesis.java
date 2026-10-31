package hkgpackagemanager.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * In-memory {@link Nemesis} for tests (not shipped).
 *
 * <ul>
 * <li>{@link #calls()} records every call as an argv-style list, built with {@link NemesisCalls}
 *     (e.g. ["install","--update","/x.zip"], ["uninstall","id"], ["pkg-list"], ["root","-c",cmd]).</li>
 * <li>Installed state is a map id -> version (null: its NEMESIS.data has no version key):
 *     {@link #setInstalled}, {@link #removeInstalled}.
 *     {@link #pkgList()} renders installed ids (sorted) as {@code id|id|0|} lines, exit 0.</li>
 * <li>{@link #uninstall(String)} removes the id and answers exit 0 "ok removed id" if it was installed,
 *     else exit 1 "not installed".</li>
 * <li>{@link #root(String)} answers {@code cat '/data/nemesis/apps/ID/NEMESIS.data'} with
 *     "[nemesis]\nid=ID\nversion=V\n" (exit 0) for installed ids and exit 1 otherwise; any other
 *     command returns exit 0 with empty output (and is recorded).</li>
 * <li>{@link #install} looks up the script for the zip path. Unscripted zips answer exit 1
 *     "no script for PATH". Scripts: {@link #scriptInstall} (fixed exit and output, no state change),
 *     {@link #scriptInstallSuccess} (marks id installed at version, exit 0 "ok installed id"),
 *     {@link #scriptInstallReboot} (marks id installed, then throws {@link Rebooted}).</li>
 * </ul>
 */
public final class FakeNemesis implements Nemesis {
    /** Thrown by a scripted install to simulate the head unit restarting mid-call. */
    public static final class Rebooted extends RuntimeException {
        public Rebooted() { super("simulated reboot"); }
    }

    private interface Script { CliResult run(); }

    private final Map<String, String> installed = new TreeMap<String, String>();
    private final Map<String, Script> scripts = new HashMap<String, Script>();
    private final List<List<String>> calls = new ArrayList<List<String>>();

    public void setInstalled(String id, String version) { installed.put(id, version); }

    public void removeInstalled(String id) { installed.remove(id); }

    public void scriptInstall(String absZipPath, final int exit, final String output) {
        scripts.put(absZipPath, new Script() { public CliResult run() { return new CliResult(exit, output); } });
    }

    public void scriptInstallSuccess(String absZipPath, final String id, final String version) {
        scripts.put(absZipPath, new Script() {
            public CliResult run() {
                installed.put(id, version);
                return new CliResult(0, "ok installed " + id + "\n");
            }
        });
    }

    public void scriptInstallReboot(String absZipPath, final String id, final String version) {
        scripts.put(absZipPath, new Script() {
            public CliResult run() {
                installed.put(id, version);
                throw new Rebooted();
            }
        });
    }

    public List<List<String>> calls() { return calls; }

    public CliResult install(String absZipPath, boolean update) {
        calls.add(NemesisCalls.install(absZipPath, update));
        Script s = scripts.get(absZipPath);
        return s == null ? new CliResult(1, "no script for " + absZipPath) : s.run();
    }

    public CliResult uninstall(String id) {
        calls.add(NemesisCalls.uninstall(id));
        if (installed.remove(id) == null) return new CliResult(1, "not installed");
        return new CliResult(0, "ok removed " + id + "\n");
    }

    public CliResult pkgList() {
        calls.add(NemesisCalls.pkgList());
        StringBuilder b = new StringBuilder();
        for (String id : installed.keySet()) b.append(id).append('|').append(id).append("|0|\n");
        return new CliResult(0, b.toString());
    }

    public CliResult root(String cmd) {
        calls.add(NemesisCalls.root(cmd));
        for (Map.Entry<String, String> e : installed.entrySet()) {
            if (cmd.equals(NemesisCalls.catCommand(NemesisCalls.installedMetaPath(e.getKey())))) {
                String v = e.getValue() == null ? "" : "version=" + e.getValue() + "\n"; // null: no version key
                return new CliResult(0, "[nemesis]\nid=" + e.getKey() + "\n" + v);
            }
        }
        if (cmd.startsWith("cat '/data/nemesis/apps/")) return new CliResult(1, "");
        return new CliResult(0, "");
    }
}
