package hkgpackagemanager.core;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class StickScannerTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    static void writeZip(File f, String... nameThenContent) throws IOException {
        f.getParentFile().mkdirs();
        ZipOutputStream z = new ZipOutputStream(new FileOutputStream(f));
        try {
            for (int i = 0; i < nameThenContent.length; i += 2) {
                z.putNextEntry(new ZipEntry(nameThenContent[i]));
                z.write(nameThenContent[i + 1].getBytes("UTF-8"));
                z.closeEntry();
            }
        } finally {
            z.close();
        }
    }

    private List<StickZip> scan() { return StickScanner.scan(tmp.getRoot()); }

    private File f(String rel) { return new File(tmp.getRoot(), rel); }

    private String problemOf(String content, String entryName) throws IOException {
        writeZip(f("a.zip"), entryName, content);
        List<StickZip> l = scan();
        assertEquals(1, l.size());
        assertNull(l.get(0).meta);
        return l.get(0).problem;
    }

    @Test public void findsRootAndPackagesFolderOnly() throws IOException {
        writeZip(f("a.zip"), "readme.txt", "x");
        writeZip(f("packages/b.ZIP"), "readme.txt", "x");
        writeZip(f("deep/c.zip"), "readme.txt", "x");
        List<StickZip> l = scan();
        assertEquals(2, l.size());
        assertEquals("a.zip", l.get(0).relPath);
        assertEquals("packages/b.ZIP", l.get(1).relPath);
    }

    @Test public void ignoresDotFilesAndOwnFolder() throws IOException {
        writeZip(f("._a.zip"), "readme.txt", "x");
        writeZip(f(".Trashes/x.zip"), "readme.txt", "x");
        writeZip(f("HKGPM/q.zip"), "readme.txt", "x");
        writeZip(f("packages/._b.zip"), "readme.txt", "x");
        assertEquals(0, scan().size());
    }

    @Test public void missingRootGivesEmptyList() {
        assertEquals(0, StickScanner.scan(f("nope")).size());
    }

    @Test public void readsMeta() throws IOException {
        writeZip(f("a.zip"), "NEMESIS.data", "[nemesis]\nid=x.y\nversion=1.2\n");
        StickZip z = scan().get(0);
        assertNull(z.problem);
        assertEquals("x.y", z.meta.id);
        assertEquals("1.2", z.meta.version);
        assertEquals(f("a.zip").length(), z.size);
    }

    @Test public void notAZip() throws IOException {
        FileOutputStream o = new FileOutputStream(f("a.zip"));
        o.write("garbage bytes, not a zip".getBytes("UTF-8"));
        o.close();
        StickZip z = scan().get(0);
        assertNull(z.meta);
        assertEquals("not a readable zip", z.problem);
    }

    @Test public void noMeta() throws IOException {
        assertEquals("no NEMESIS.data at the zip's root", problemOf("x", "readme.txt"));
    }

    @Test public void wrappedFolderGetsFolderReason() throws IOException {
        assertEquals("NEMESIS.data is inside a folder in the zip, not at its root",
                problemOf("[nemesis]\nid=x.y\n", "tool/NEMESIS.data"));
    }

    @Test public void bundleZipGetsBundleReason() throws IOException {
        assertEquals("this is a USB install stick, not a package: copy its packages/ zip instead",
                problemOf("[nemesis]\ntype=install\npath=packages/x.zip\n", "NEMESIS.data"));
    }

    @Test public void noIdReason() throws IOException {
        assertEquals("no package id in NEMESIS.data", problemOf("[nemesis]\nname=x\n", "NEMESIS.data"));
    }

    @Test public void badIdReason() throws IOException {
        assertEquals("invalid package id: a..b", problemOf("[nemesis]\nid=a..b\n", "NEMESIS.data"));
    }

    @Test public void oversizeMetaReadsFirst8192Bytes() throws IOException {
        StringBuilder sb = new StringBuilder("[nemesis]\nid=x.y\nversion=3\n");
        while (sb.length() < 20000) sb.append("# padding padding padding\n");
        writeZip(f("a.zip"), "NEMESIS.data", sb.toString());
        assertEquals("x.y", scan().get(0).meta.id);
    }

    @Test public void spacesAndUnicodeInNames() throws IOException {
        String d = "[nemesis]\nid=x.y\n";
        writeZip(f("Enable ADB.zip"), "NEMESIS.data", d);
        writeZip(f("naïve $x'.zip"), "NEMESIS.data", d);
        List<StickZip> l = scan();
        assertEquals(2, l.size());
        assertNotNull(l.get(0).meta);
        assertNotNull(l.get(1).meta);
    }
}
