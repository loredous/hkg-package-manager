package hkgpackagemanager.app;

import android.content.Context;
import hkgpackagemanager.core.CliResult;
import hkgpackagemanager.core.Nemesis;
import hkgpackagemanager.core.NemesisCalls;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/** The NEMESIS CLI over ProcessBuilder; stderr is merged, output capped at 512 KiB like the daemon. */
public final class NemesisCli implements Nemesis {
    static final String BINARY = "/system/xbin/nemesis";
    static final int MAX_OUTPUT = 512 * 1024;

    private final AppLog log;

    public NemesisCli(Context c) {
        this.log = AppLog.get(c);
    }

    @Override public CliResult install(String absZipPath, boolean update) { return run(NemesisCalls.install(absZipPath, update)); }
    @Override public CliResult uninstall(String id) { return run(NemesisCalls.uninstall(id)); }
    @Override public CliResult pkgList() { return run(NemesisCalls.pkgList()); }
    @Override public CliResult root(String shellCommand) { return run(NemesisCalls.root(shellCommand)); }

    private CliResult run(List<String> argv) {
        List<String> cmd = new ArrayList<String>();
        cmd.add(BINARY);
        cmd.addAll(argv);
        CliResult r;
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            try {
                p.getOutputStream().close();
            } catch (IOException e) {
                // already closed
            }
            String out = readCapped(p.getInputStream());
            int exit;
            try {
                exit = p.waitFor();
            } catch (InterruptedException e) {
                p.destroy();
                Thread.currentThread().interrupt();
                exit = 130;
            }
            r = new CliResult(exit, out);
        } catch (IOException e) {
            r = new CliResult(127, String.valueOf(e.getMessage()));
        }
        log.log("nemesis " + argv + " -> exit " + r.exit + ", " + r.lastLine());
        return r;
    }

    /** Reads everything (so the child never blocks on a full pipe) but keeps only the first MAX_OUTPUT bytes. */
    private static String readCapped(InputStream in) throws IOException {
        ByteArrayOutputStream kept = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        try {
            while ((n = in.read(buf)) > 0) {
                int room = MAX_OUTPUT - kept.size();
                if (room > 0) kept.write(buf, 0, Math.min(room, n));
            }
        } finally {
            in.close();
        }
        return new String(kept.toByteArray(), "UTF-8");
    }
}
