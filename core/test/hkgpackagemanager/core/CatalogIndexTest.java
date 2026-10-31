package hkgpackagemanager.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import org.junit.Test;

public class CatalogIndexTest {
    private static final String SHA = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private static String entry(String id, String extra) {
        return "{\"id\":\"" + id + "\",\"name\":\"N\",\"version\":\"1\",\"description\":\"d\","
                + "\"author\":\"a\",\"zip\":\"zips/" + id + ".zip\",\"size\":12,\"sha256\":\"" + SHA + "\""
                + extra + "}";
    }

    private static String index(String header, String... entries) {
        StringBuilder sb = new StringBuilder("{" + header + ",\"packages\":[");
        for (int i = 0; i < entries.length; i++) sb.append(i > 0 ? "," : "").append(entries[i]);
        return sb.append("]}").toString();
    }

    private static final String HEADER = "\"format\":1,\"id\":\"cat\",\"name\":\"C\","
            + "\"publisher\":\"P\",\"generated\":\"2026-11-01T00:00:00Z\",\"key\":\"" + SHA + "\"";

    private static CatalogIndex parse(String s) throws Exception {
        return CatalogIndex.parse(s.getBytes("UTF-8"));
    }

    private static String refusal(byte[] b) {
        try {
            CatalogIndex.parse(b);
        } catch (BadCatalogException e) {
            return e.getMessage();
        }
        fail("expected BadCatalogException");
        return null;
    }

    private static String refusal(String s) throws Exception {
        return refusal(s.getBytes("UTF-8"));
    }

    private static String problemOfOnly(String entryJson) throws Exception {
        CatalogIndex c = parse(index(HEADER, entryJson));
        assertEquals(1, c.entries.size());
        return c.entries.get(0).problem;
    }

    @Test public void parsesSample() throws Exception {
        InputStream in = new FileInputStream("tests/catalog/sample/HKGPM/catalogs/sample/index.json");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        CatalogIndex c = CatalogIndex.parse(out.toByteArray());
        assertEquals(1, c.format);
        assertEquals("sample", c.id);
        assertEquals("Sample catalog", c.name);
        assertEquals(5, c.entries.size());
        CatalogEntry g = c.entries.get(0);
        assertEquals("sample.green", g.id);
        assertNull(g.problem);
        assertEquals(324L, g.size);
        assertEquals("zips/sample.green.zip", g.localPath());
        assertFalse(g.isRemote());
        assertEquals("NEEV23.*", g.compat.worksOn.get(0).build);
        for (CatalogEntry e : c.entries) assertNull(e.problem);
    }

    @Test public void newerFormatRefused() throws Exception {
        assertEquals("This catalog needs a newer HKG Package Manager (format 2).",
                refusal(index(HEADER.replace("\"format\":1", "\"format\":2"))));
    }

    @Test public void missingHeaderFieldRefused() throws Exception {
        String m = refusal(index(HEADER.replace("\"publisher\":\"P\",", "")));
        assertTrue(m, m.contains("publisher"));
    }

    @Test public void invalidCatalogIdRefused() throws Exception {
        String m = refusal(index(HEADER.replace("\"id\":\"cat\"", "\"id\":\"../x\"")));
        assertTrue(m, m.contains("id"));
    }

    @Test public void notJsonRefused() throws Exception {
        assertEquals("index.json is not valid JSON", refusal("nope"));
        assertEquals("index.json is not valid JSON", refusal(new byte[0]));
    }

    @Test public void bomIsAccepted() throws Exception {
        byte[] body = index(HEADER, entry("a.b", "")).getBytes("UTF-8");
        byte[] b = new byte[body.length + 3];
        b[0] = (byte) 0xEF; b[1] = (byte) 0xBB; b[2] = (byte) 0xBF;
        System.arraycopy(body, 0, b, 3, body.length);
        assertEquals(1, CatalogIndex.parse(b).entries.size());
    }

    @Test public void tooBigRefused() throws Exception {
        byte[] b = new byte[CatalogIndex.MAX_BYTES + 1];
        java.util.Arrays.fill(b, (byte) ' ');
        String m = refusal(b);
        assertTrue(m, m.contains("too big"));
    }

    @Test public void wrongTypesInvalidateOnlyThatEntry() throws Exception {
        String bad = entry("a.bad", "").replace("\"size\":12", "\"size\":\"12\"");
        CatalogIndex c = parse(index(HEADER, bad, entry("a.good", "")));
        assertEquals("invalid catalog entry: size", c.entries.get(0).problem);
        assertNull(c.entries.get(0).localPath());
        assertNull(c.entries.get(1).problem);
    }

    @Test public void negativeOrFractionalSizeInvalid() throws Exception {
        assertEquals("invalid catalog entry: size",
                problemOfOnly(entry("a.b", "").replace("\"size\":12", "\"size\":-1")));
        assertEquals("invalid catalog entry: size",
                problemOfOnly(entry("a.b", "").replace("\"size\":12", "\"size\":1.5")));
        assertNull(problemOfOnly(entry("a.b", "").replace("\"size\":12", "\"size\":5000000000")));
    }

    @Test public void missingRequiredEntryFieldsInvalid() throws Exception {
        assertEquals("invalid catalog entry: description",
                problemOfOnly(entry("a.b", "").replace("\"description\":\"d\",", "")));
        assertEquals("invalid catalog entry: author",
                problemOfOnly(entry("a.b", "").replace("\"author\":\"a\",", "")));
        assertEquals("invalid catalog entry: id", problemOfOnly(entry("../x", "")));
        assertEquals("invalid catalog entry: homepage", problemOfOnly(entry("a.b", ",\"homepage\":3")));
        assertEquals("invalid catalog entry", problemOfOnly("5"));
    }

    @Test public void badCompatibilityInvalidatesEntry() throws Exception {
        assertEquals("invalid catalog entry: not_on",
                problemOfOnly(entry("a.b", ",\"compatibility\":{\"not_on\":\"x\"}")));
        assertEquals("invalid catalog entry: compatibility",
                problemOfOnly(entry("a.b", ",\"compatibility\":[]")));
        assertEquals("invalid catalog entry: works_on",
                problemOfOnly(entry("a.b", ",\"compatibility\":{\"works_on\":{}}")));
        assertEquals("invalid catalog entry: works_on",
                problemOfOnly(entry("a.b", ",\"compatibility\":{\"works_on\":[\"x\"]}")));
        assertEquals("invalid catalog entry: works_on",
                problemOfOnly(entry("a.b", ",\"compatibility\":{\"works_on\":[{\"build\":3}]}")));
        assertEquals("invalid catalog entry: works_on",
                problemOfOnly(entry("a.b", ",\"compatibility\":{\"works_on\":[{\"hardware\":\"wifi\"}]}")));
        assertEquals("invalid catalog entry: works_on",
                problemOfOnly(entry("a.b", ",\"compatibility\":{\"works_on\":[{\"hardware\":[1]}]}")));
        assertEquals("invalid catalog entry: not_on",
                problemOfOnly(entry("a.b", ",\"compatibility\":{\"not_on\":[null]}")));
        assertEquals("invalid catalog entry: conflicts",
                problemOfOnly(entry("a.b", ",\"compatibility\":{\"conflicts\":\"x.y\"}")));
        assertEquals("invalid catalog entry: conflicts",
                problemOfOnly(entry("a.b", ",\"compatibility\":{\"conflicts\":[3]}")));
        assertEquals("invalid catalog entry: conflicts",
                problemOfOnly(entry("a.b", ",\"compatibility\":{\"conflicts\":[\"../x\"]}")));
    }

    @Test public void goodCompatibilityParsed() throws Exception {
        CatalogIndex c = parse(index(HEADER, entry("a.b", ",\"compatibility\":{"
                + "\"works_on\":[{\"build\":\"X*\",\"hardware\":[\"wifi\"]},{}],"
                + "\"not_on\":[{\"hardware\":[\"!wifi\"]}],\"conflicts\":[\"c.d\"],\"extra\":1}")));
        CatalogEntry e = c.entries.get(0);
        assertNull(e.problem);
        assertEquals(2, e.compat.worksOn.size());
        assertEquals("X*", e.compat.worksOn.get(0).build);
        assertEquals("wifi", e.compat.worksOn.get(0).hardware.get(0));
        assertFalse(e.compat.worksOn.get(1).isValid());
        assertEquals("!wifi", e.compat.notOn.get(0).hardware.get(0));
        assertEquals("c.d", e.compat.conflicts.get(0));
        assertSame(Compat.NONE, parse(index(HEADER, entry("a.c", ""))).entries.get(0).compat);
    }

    private static void assertSame(Object a, Object b) {
        org.junit.Assert.assertSame(a, b);
    }

    @Test public void duplicateIdSecondIsInvalid() throws Exception {
        CatalogIndex c = parse(index(HEADER, entry("a.b", ""), entry("a.b", "")));
        assertNull(c.entries.get(0).problem);
        assertEquals("duplicate package id in this catalog", c.entries.get(1).problem);
    }

    @Test public void unknownFieldsIgnored() throws Exception {
        CatalogIndex c = parse(index(HEADER + ",\"future\":{\"x\":1}", entry("a.b", ",\"colour\":\"red\"")));
        assertNull(c.entries.get(0).problem);
    }

    @Test public void shaIsLowercasedAndChecked() throws Exception {
        CatalogEntry e = parse(index(HEADER, entry("a.b", "").replace(SHA, SHA.toUpperCase()))).entries.get(0);
        assertEquals(SHA, e.sha256);
        assertEquals("invalid catalog entry: sha256", problemOfOnly(entry("a.b", "").replace(SHA, SHA.substring(1))));
        assertEquals("invalid catalog entry: sha256",
                problemOfOnly(entry("a.b", "").replace(SHA, "g" + SHA.substring(1))));
    }

    @Test public void badShaInvalid() throws Exception {
        String p = problemOfOnly(entry("a.b", "").replace(SHA, SHA.substring(1)));
        assertNotNull(p);
        assertTrue(p, p.contains("sha256"));
    }

    @Test public void zipLocationThatEscapesIsInvalid() throws Exception {
        String[] bad = {"../x.zip", "/system/x.zip", "a/../../x.zip", "a\\\\b.zip", "", "a//b.zip",
            "remote/x.zip", "ftp://h/x.zip", "./x.zip", "a/./x.zip", "a/",
            "C:x.zip", "a:b.zip", "a/b\\u0001.zip", "a/b\\u0000.zip", "Remote/x.zip", "REMOTE/x.zip",
            "KEY.PEM", "Index.Json", "index.json.SIG", "zips/a.ZIP.PART", "a.part", "zips/a&b.zip"};
        for (String z : bad) {
            String j = entry("a.b", "").replace("\"zips/a.b.zip\"", "\"" + z + "\"");
            assertEquals(z, "unsupported zip location", problemOfOnly(j));
        }
    }

    @Test public void urlZipStoredUnderRemote() throws Exception {
        CatalogEntry e = parse(index(HEADER,
                entry("a.b", "").replace("zips/a.b.zip", "https://h/p/x.zip"))).entries.get(0);
        assertNull(e.problem);
        assertEquals("remote/a.b.zip", e.localPath());
        assertTrue(e.isRemote());
        assertEquals("https://h/p/x.zip", e.zip);
    }

    @Test public void deeplyNestedJsonIsCleanError() throws Exception {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100000; i++) sb.append('[');
        assertEquals("index.json is not valid JSON", refusal(sb.toString()));
    }
}
