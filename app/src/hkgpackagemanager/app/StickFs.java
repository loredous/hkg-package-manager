package hkgpackagemanager.app;

import android.content.Context;
import hkgpackagemanager.core.CliResult;
import hkgpackagemanager.core.FileFingerprint;
import hkgpackagemanager.core.MountTable;
import hkgpackagemanager.core.Nemesis;
import hkgpackagemanager.core.NemesisCalls;
import hkgpackagemanager.core.ShellQuote;
import hkgpackagemanager.core.StageName;
import hkgpackagemanager.core.StickFiles;
import hkgpackagemanager.core.ZipCheck;
import hkgpackagemanager.core.ZipFingerprint;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** The stick as files: direct writes first, root (via the NEMESIS CLI) when the app uid is refused. */
public final class StickFs implements StickFiles, ZipCheck {
    static final String NOT_MOUNTED = "USB drive not mounted";

    private final File stage;
    private final String root;
    private final Nemesis nemesis;
    private final AppLog log;
    private volatile boolean usedRoot;

    public StickFs(Context c, String stickRoot, Nemesis n) {
        this.stage = new File(c.getFilesDir(), "stage");
        this.root = stickRoot;
        this.nemesis = n;
        this.log = AppLog.get(c);
    }

    public boolean lastWriteUsedRoot() { return usedRoot; }

    private File file(String rel) { return new File(root + "/" + rel); }

    @Override public boolean exists(String rel) { return file(rel).exists(); }

    @Override public byte[] read(String rel) throws IOException {
        InputStream in = new FileInputStream(file(rel));
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        } finally {
            in.close();
        }
    }

    /**
     * Ruling R19: refuse to change anything unless the stick root is a mount point right now. A pulled
     * stick leaves /storage/usbN as an empty folder on the /storage tmpfs; without this check the root
     * fallback's mkdir -p would recreate HKGPM there (in RAM) and the queue would never stop.
     */
    private void requireMounted() throws IOException {
        String mounts;
        try {
            mounts = readMounts();
        } catch (IOException e) {
            throw new IOException("USB drive not mounted (cannot read /proc/mounts: " + e.getMessage() + ")");
        }
        if (!MountTable.isMounted(mounts, root)) {
            // keep the evidence in case this head unit names its mounts differently (R19's named risk)
            StringBuilder usb = new StringBuilder();
            for (String line : mounts.split("\n")) {
                if (line.indexOf("usb") >= 0 || line.indexOf("media_rw") >= 0) usb.append("\n  ").append(line);
            }
            log.log("refused to change " + root + ": not in /proc/mounts" + (usb.length() == 0 ? "" : ":" + usb));
            throw new IOException(NOT_MOUNTED);
        }
    }

    private static String readMounts() throws IOException {
        InputStream in = new FileInputStream("/proc/mounts");
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return new String(out.toByteArray(), "UTF-8");
        } finally {
            in.close();
        }
    }

    @Override public void writeAtomic(String rel, byte[] data) throws IOException {
        requireMounted();
        try {
            writeDirect(rel, data);
            usedRoot = false;
            return;
        } catch (IOException e) {
            log.log("direct write of " + rel + " failed (" + e.getMessage() + "), using root");
        } catch (SecurityException e) {
            log.log("direct write of " + rel + " refused (" + e.getMessage() + "), using root");
        }
        stage.mkdirs();
        File staged = new File(stage, StageName.flatten(rel));
        FileOutputStream out = new FileOutputStream(staged);
        try {
            out.write(data);
            out.getFD().sync();
        } finally {
            out.close();
        }
        try {
            check(nemesis.root(NemesisCalls.copyIntoCommand(staged.getAbsolutePath(), root + "/" + rel)));
            usedRoot = true;
        } finally {
            staged.delete();
        }
    }

    private void writeDirect(String rel, byte[] data) throws IOException {
        File target = file(rel);
        File dir = target.getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory()) {
            throw new IOException("cannot create " + dir);
        }
        File tmp = new File(target.getPath() + ".tmp");
        FileOutputStream out = new FileOutputStream(tmp);
        try {
            out.write(data);
            out.getFD().sync();
        } finally {
            out.close();
        }
        // rename replaces an existing file (vfat, FUSE sdcard); never delete the target first, so a
        // power loss leaves either the old or the new file. On failure the root path (cp + mv) takes over.
        if (!tmp.renameTo(target)) {
            tmp.delete();
            throw new IOException("cannot rename into " + target);
        }
    }

    @Override public void move(String fromRel, String toRel) throws IOException {
        requireMounted();
        File from = file(fromRel);
        File to = file(toRel);
        try {
            File dir = to.getParentFile();
            if (dir != null) dir.mkdirs();
            if (from.renameTo(to)) {
                usedRoot = false;
                return;
            }
        } catch (SecurityException e) {
            // fall through to root
        }
        check(nemesis.root("mv -f " + ShellQuote.quote(from.getPath()) + " " + ShellQuote.quote(to.getPath())));
        usedRoot = true;
    }

    @Override public void delete(String rel) throws IOException {
        requireMounted();
        File f = file(rel);
        try {
            if (!f.exists() || f.delete()) {
                usedRoot = false;
                return;
            }
        } catch (SecurityException e) {
            // fall through to root
        }
        check(nemesis.root("rm -f " + ShellQuote.quote(f.getPath())));
        usedRoot = true;
    }

    private static void check(CliResult r) throws IOException {
        if (r.exit != 0) throw new IOException(r.lastLine() != null ? r.lastLine() : "exit " + r.exit);
    }

    @Override public ZipFingerprint fingerprint(String relPath) throws IOException {
        return FileFingerprint.of(file(relPath));
    }
}
