package hkgpackagemanager.app;

import android.content.Context;
import hkgpackagemanager.core.CliResult;
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** The stick as files: direct writes first, root (via the NEMESIS CLI) when the app uid is refused. */
public final class StickFs implements StickFiles, ZipCheck {
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

    @Override public void writeAtomic(String rel, byte[] data) throws IOException {
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
        // FAT rename may not replace an existing file
        if (target.exists() && !target.delete()) {
            tmp.delete();
            throw new IOException("cannot replace " + target);
        }
        if (!tmp.renameTo(target)) {
            tmp.delete();
            throw new IOException("cannot rename into " + target);
        }
    }

    @Override public void move(String fromRel, String toRel) throws IOException {
        File from = file(fromRel);
        File to = file(toRel);
        try {
            File dir = to.getParentFile();
            if (dir != null) dir.mkdirs();
            if (to.exists()) to.delete();
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
        if (r.exit != 0) throw new IOException(String.valueOf(r.lastLine()));
    }

    @Override public ZipFingerprint fingerprint(String relPath) throws IOException {
        File f = file(relPath);
        if (!f.isFile()) return null;
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("no SHA-256");
        }
        long size = 0;
        InputStream in = new FileInputStream(f);
        try {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                md.update(buf, 0, n);
                size += n;
            }
        } finally {
            in.close();
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : md.digest()) {
            hex.append(Character.forDigit((b >> 4) & 0xf, 16)).append(Character.forDigit(b & 0xf, 16));
        }
        return new ZipFingerprint(size, hex.toString());
    }
}
