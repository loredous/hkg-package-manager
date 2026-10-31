package hkgpackagemanager.core;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Reads and writes the queue file (JSON, format 1). */
public final class QueueCodec {
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final int FORMAT = 1;

    private QueueCodec() {}

    public static final class Unreadable extends Exception {
        public Unreadable(String why) { super(why); }
    }

    public static byte[] encode(Queue q) {
        try {
            JSONArray arr = new JSONArray();
            for (QueueItem i : q.items) {
                JSONObject o = new JSONObject();
                o.put("relPath", i.relPath);
                o.put("size", i.size);
                o.put("sha256", i.sha256);
                o.put("id", i.id);
                o.put("version", i.version);
                o.put("update", i.update);
                o.put("state", i.state.name());
                o.put("message", i.message);
                arr.put(o);
            }
            JSONObject root = new JSONObject();
            root.put("format", FORMAT);
            root.put("finished", q.finished);
            root.put("items", arr);
            return root.toString(2).getBytes(UTF8);
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    public static Queue decode(byte[] data) throws Unreadable {
        try {
            JSONObject root = new JSONObject(new String(data, UTF8));
            if (root.getInt("format") != FORMAT) throw new Unreadable("unsupported format");
            boolean finished = bool(root, "finished");
            JSONArray arr = root.getJSONArray("items");
            List<QueueItem> items = new ArrayList<QueueItem>();
            for (int n = 0; n < arr.length(); n++) {
                Object el = arr.get(n);
                if (!(el instanceof JSONObject)) throw new Unreadable("item is not an object");
                JSONObject o = (JSONObject) el;
                items.add(new QueueItem(str(o, "relPath"), num(o, "size"), str(o, "sha256"),
                        str(o, "id"), str(o, "version"), bool(o, "update"),
                        ItemState.valueOf(str(o, "state")), str(o, "message")));
            }
            return new Queue(finished, items);
        } catch (JSONException e) {
            throw new Unreadable(String.valueOf(e.getMessage()));
        } catch (IllegalArgumentException e) {
            throw new Unreadable("unknown state");
        } catch (RuntimeException e) {
            throw new Unreadable(String.valueOf(e));
        }
    }

    // org.json coerces across types (getString(5) -> "5"); check the type ourselves.
    private static String str(JSONObject o, String k) throws JSONException, Unreadable {
        if (!(o.get(k) instanceof String)) throw new Unreadable(k + " is not a string");
        return o.getString(k);
    }

    private static boolean bool(JSONObject o, String k) throws JSONException, Unreadable {
        if (!(o.get(k) instanceof Boolean)) throw new Unreadable(k + " is not a boolean");
        return o.getBoolean(k);
    }

    private static long num(JSONObject o, String k) throws JSONException, Unreadable {
        Object v = o.get(k);
        if (!(v instanceof Integer || v instanceof Long)) throw new Unreadable(k + " is not an integer");
        return o.getLong(k);
    }
}
