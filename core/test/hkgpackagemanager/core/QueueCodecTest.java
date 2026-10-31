package hkgpackagemanager.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.nio.charset.Charset;
import java.util.Arrays;
import org.junit.Test;

public class QueueCodecTest {
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private static QueueItem item(String rel, ItemState s, String msg) {
        return new QueueItem(rel, 1234L, "ab12", "adb.tcp", "1.0", false, s, msg);
    }

    private static String itemJson(String override) {
        String base = "\"relPath\":\"a.zip\",\"size\":1,\"sha256\":\"x\",\"id\":\"i\","
                + "\"version\":\"1\",\"update\":false,\"state\":\"WAITING\",\"message\":\"\"";
        String key = override.substring(0, override.indexOf(':'));
        String out = base.replaceAll(key + ":(\"[^\"]*\"|[^,]*)", java.util.regex.Matcher.quoteReplacement(override));
        return "{\"format\":1,\"finished\":false,\"items\":[{" + out + "}]}";
    }

    private static void assertUnreadable(String json) {
        try {
            QueueCodec.decode(json.getBytes(UTF8));
            fail("expected Unreadable for: " + json);
        } catch (QueueCodec.Unreadable expected) {
            // ok
        }
    }

    @Test public void roundTrip() throws Exception {
        Queue q = new Queue(false, Arrays.asList(
                item("Enable ADB.zip", ItemState.DONE, ""),
                item("packages/x.zip", ItemState.FAILED, "install failed: a\tb\nc\\d")));
        assertEquals(q, QueueCodec.decode(QueueCodec.encode(q)));
    }

    @Test public void finishedRoundTrips() throws Exception {
        Queue q = new Queue(true, Arrays.asList(item("a.zip", ItemState.DONE, null)));
        assertEquals(q, QueueCodec.decode(QueueCodec.encode(q)));
    }

    @Test public void messageWithQuotesAndNewlinesSurvives() throws Exception {
        Queue q = new Queue(false, Arrays.asList(item("a.zip", ItemState.FAILED, "a \"b\"\nc\\d")));
        assertEquals("a \"b\"\nc\\d", QueueCodec.decode(QueueCodec.encode(q)).items.get(0).message);
    }

    @Test public void encodeIsIndentedUtf8() {
        Queue q = new Queue(false, Arrays.asList(item("a.zip", ItemState.WAITING, "")));
        String s = new String(QueueCodec.encode(q), UTF8);
        org.junit.Assert.assertTrue(s.contains("\n  \"format\": 1"));
    }

    @Test public void rejectsTruncatedAndNewerFormat() {
        assertUnreadable("");
        assertUnreadable("{\"format\":1,\"finished\":false,\"items\":[{\"relPath\":\"a.zip\"");
        assertUnreadable("{\"format\":2,\"finished\":false,\"items\":[]}");
        assertUnreadable("{\"format\":1,\"finished\":false,\"items\":[{\"relPath\":\"a.zip\"}]}");
        assertUnreadable(itemJson("\"state\":\"BOGUS\""));
        assertUnreadable(itemJson("\"size\":\"big\""));
    }

    @Test public void rejectsWrongTypesAndMissingTopLevel() {
        assertUnreadable(itemJson("\"update\":\"false\""));
        assertUnreadable(itemJson("\"relPath\":5"));
        assertUnreadable("{\"finished\":false,\"items\":[]}");
        assertUnreadable("{\"format\":1,\"items\":[]}");
        assertUnreadable("{\"format\":1,\"finished\":false}");
        assertUnreadable("{\"format\":1,\"finished\":false,\"items\":[5]}");
        assertUnreadable("[]");
    }

    @Test public void validItemJsonDecodes() throws Exception {
        Queue q = QueueCodec.decode(itemJson("\"size\":7").getBytes(UTF8));
        assertEquals(7L, q.items.get(0).size);
    }
}
