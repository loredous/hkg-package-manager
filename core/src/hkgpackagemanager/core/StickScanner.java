package hkgpackagemanager.core;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Finds package zips in the stick root and its packages/ folder and reads their NEMESIS.data. */
public final class StickScanner {
    static final int META_LIMIT = 8192; // NEMESIS reads at most this much of NEMESIS.data
    static final String META_NAME = "NEMESIS.data";

    private StickScanner() {}

    public static List<StickZip> scan(File stickRoot) {
        List<StickZip> out = new ArrayList<StickZip>();
        scanDir(stickRoot, "", out);
        scanDir(new File(stickRoot, "packages"), "packages/", out);
        Collections.sort(out, new Comparator<StickZip>() {
            public int compare(StickZip a, StickZip b) {
                return a.relPath.toLowerCase(Locale.ROOT).compareTo(b.relPath.toLowerCase(Locale.ROOT));
            }
        });
        return out;
    }

    private static void scanDir(File dir, String prefix, List<StickZip> out) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            String name = f.getName();
            if (name.startsWith(".") || !f.isFile()) continue;
            if (!name.toLowerCase(Locale.ROOT).endsWith(".zip")) continue;
            out.add(inspect(f, prefix + name));
        }
    }

    public static StickZip inspect(File f, String relPath) {
        long size = f.length();
        ZipFile zip;
        try {
            zip = new ZipFile(f);
        } catch (IOException e) {
            return StickZip.problem(relPath, size, "not a readable zip");
        } catch (RuntimeException e) {
            return StickZip.problem(relPath, size, "not a readable zip");
        }
        try {
            ZipEntry entry = zip.getEntry(META_NAME);
            if (entry == null) {
                Enumeration<? extends ZipEntry> all = zip.entries();
                while (all.hasMoreElements()) {
                    if (all.nextElement().getName().endsWith("/" + META_NAME)) {
                        return StickZip.problem(relPath, size,
                                "NEMESIS.data is inside a folder in the zip, not at its root");
                    }
                }
                return StickZip.problem(relPath, size, "no NEMESIS.data at the zip's root");
            }
            IniFile ini = IniFile.parse(readHead(zip, entry));
            String id = ini.get("nemesis", "id");
            String type = ini.get("nemesis", "type");
            if ((id == null || id.length() == 0) && type != null && type.length() > 0) {
                return StickZip.problem(relPath, size,
                        "this is a USB install stick, not a package: copy its packages/ zip instead");
            }
            try {
                return StickZip.ok(relPath, size, PackageMeta.from(ini));
            } catch (BadPackageException e) {
                return StickZip.problem(relPath, size, e.getMessage());
            }
        } catch (IOException e) {
            return StickZip.problem(relPath, size, "not a readable zip");
        } catch (RuntimeException e) {
            return StickZip.problem(relPath, size, "not a readable zip");
        } finally {
            try {
                zip.close();
            } catch (IOException ignored) {
                // nothing to do
            }
        }
    }

    private static String readHead(ZipFile zip, ZipEntry entry) throws IOException {
        InputStream in = zip.getInputStream(entry);
        try {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] chunk = new byte[1024];
            while (buf.size() < META_LIMIT) {
                int n = in.read(chunk, 0, Math.min(chunk.length, META_LIMIT - buf.size()));
                if (n < 0) break;
                buf.write(chunk, 0, n);
            }
            return new String(buf.toByteArray(), "UTF-8");
        } finally {
            in.close();
        }
    }
}
