package hkgpackagemanager.core;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** A parsed catalog index.json: header fields plus entries (bad entries carry a problem). */
public final class CatalogIndex {
    public static final int SUPPORTED_FORMAT = 1;
    public static final int MAX_BYTES = 2 * 1024 * 1024;

    public final int format;
    public final String id;
    public final String name;
    public final String publisher;
    public final String generated;
    public final String key;
    public final List<CatalogEntry> entries;

    private CatalogIndex(int format, String id, String name, String publisher, String generated,
            String key, List<CatalogEntry> entries) {
        this.format = format;
        this.id = id;
        this.name = name;
        this.publisher = publisher;
        this.generated = generated;
        this.key = key;
        this.entries = Collections.unmodifiableList(entries);
    }

    /** Parses index.json bytes; header problems throw, entry problems are kept on the entry. */
    public static CatalogIndex parse(byte[] bytes) throws BadCatalogException {
        if (bytes.length > MAX_BYTES) throw new BadCatalogException("index.json is too big");
        int start = 0;
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF) start = 3;
        JSONObject root;
        try {
            root = new JSONObject(new String(bytes, start, bytes.length - start, "UTF-8"));
        } catch (JSONException e) {
            throw new BadCatalogException("index.json is not valid JSON");
        } catch (RuntimeException e) {
            throw new BadCatalogException("index.json is not valid JSON");
        } catch (StackOverflowError e) {
            throw new BadCatalogException("index.json is not valid JSON");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
        Object f = root.opt("format");
        if (!isInt(f) || ((Number) f).longValue() < 1) throw bad("format");
        long format = ((Number) f).longValue();
        if (format > SUPPORTED_FORMAT) {
            throw new BadCatalogException(
                    "This catalog needs a newer HKG Package Manager (format " + format + ").");
        }
        String id = headerString(root, "id");
        if (!PackageId.isValid(id)) throw bad("id");
        String name = headerString(root, "name");
        String publisher = headerString(root, "publisher");
        String generated = headerString(root, "generated");
        String key = headerString(root, "key");
        Object p = root.opt("packages");
        if (!(p instanceof JSONArray)) throw bad("packages");
        JSONArray arr = (JSONArray) p;
        List<CatalogEntry> entries = new ArrayList<CatalogEntry>();
        Set<String> seen = new HashSet<String>();
        for (int i = 0; i < arr.length(); i++) {
            CatalogEntry e = entry(arr.opt(i));
            if (e.problem == null && !seen.add(e.id)) {
                e = CatalogEntry.invalid(e.id, e.name, "duplicate package id in this catalog");
            }
            entries.add(e);
        }
        return new CatalogIndex((int) format, id, name, publisher, generated, key, entries);
    }

    private static BadCatalogException bad(String field) {
        return new BadCatalogException("index.json has a missing or invalid field: " + field);
    }

    private static boolean isInt(Object o) {
        return o instanceof Integer || o instanceof Long;
    }

    private static String headerString(JSONObject o, String field) throws BadCatalogException {
        Object v = o.opt(field);
        if (!(v instanceof String)) throw bad(field);
        return (String) v;
    }

    /** A field-named entry problem. */
    private static final class Bad extends Exception {
        private static final long serialVersionUID = 1L;
        Bad(String field) {
            super(field == null ? "invalid catalog entry" : "invalid catalog entry: " + field);
        }
    }

    private static String str(JSONObject o, String field, boolean required) throws Bad {
        Object v = o.opt(field);
        if (v == null) {
            if (required) throw new Bad(field);
            return null;
        }
        if (!(v instanceof String)) throw new Bad(field);
        return (String) v;
    }

    private static CatalogEntry entry(Object o) {
        if (!(o instanceof JSONObject)) return CatalogEntry.invalid(null, null, new Bad(null).getMessage());
        JSONObject j = (JSONObject) o;
        Object rawId = j.opt("id");
        Object rawName = j.opt("name");
        String idHint = rawId instanceof String ? (String) rawId : null;
        String nameHint = rawName instanceof String ? (String) rawName : null;
        try {
            String id = str(j, "id", true);
            if (!PackageId.isValid(id)) throw new Bad("id");
            String name = str(j, "name", true);
            String version = str(j, "version", true);
            String description = str(j, "description", true);
            String author = str(j, "author", true);
            String homepage = str(j, "homepage", false);
            String zip = str(j, "zip", true);
            Object s = j.opt("size");
            if (!isInt(s) || ((Number) s).longValue() < 0) throw new Bad("size");
            String sha = str(j, "sha256", true);
            if (!isSha(sha)) throw new Bad("sha256");
            Compat compat = compat(j.opt("compatibility"));
            if (!goodZip(zip)) {
                return CatalogEntry.invalid(id, name, "unsupported zip location");
            }
            return new CatalogEntry(id, name, version, description, author, homepage, zip,
                    ((Number) s).longValue(), sha.toLowerCase(java.util.Locale.US), compat, null);
        } catch (Bad b) {
            return CatalogEntry.invalid(idHint, nameHint, b.getMessage());
        }
    }

    private static boolean isSha(String s) {
        if (s.length() != 64) return false;
        for (int i = 0; i < 64; i++) {
            char c = s.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) return false;
        }
        return true;
    }

    private static boolean goodZip(String z) {
        if (CatalogEntry.isUrl(z)) return true;
        if (z.length() == 0 || z.startsWith("/") || z.startsWith("remote/") || z.indexOf('\\') >= 0) return false;
        int from = 0;
        while (true) {
            int slash = z.indexOf('/', from);
            String seg = z.substring(from, slash < 0 ? z.length() : slash);
            if (seg.length() == 0 || seg.equals(".") || seg.equals("..")) return false;
            if (slash < 0) return true;
            from = slash + 1;
        }
    }

    private static Compat compat(Object c) throws Bad {
        if (c == null) return Compat.NONE;
        if (!(c instanceof JSONObject)) throw new Bad("compatibility");
        JSONObject o = (JSONObject) c;
        List<CompatRule> works = rules(o, "works_on");
        List<CompatRule> not = rules(o, "not_on");
        List<String> conflicts = new ArrayList<String>();
        Object cf = o.opt("conflicts");
        if (cf != null) {
            if (!(cf instanceof JSONArray)) throw new Bad("conflicts");
            JSONArray a = (JSONArray) cf;
            for (int i = 0; i < a.length(); i++) {
                Object v = a.opt(i);
                if (!(v instanceof String) || !PackageId.isValid((String) v)) throw new Bad("conflicts");
                conflicts.add((String) v);
            }
        }
        return new Compat(works, not, conflicts);
    }

    private static List<CompatRule> rules(JSONObject o, String field) throws Bad {
        List<CompatRule> out = new ArrayList<CompatRule>();
        Object v = o.opt(field);
        if (v == null) return out;
        if (!(v instanceof JSONArray)) throw new Bad(field);
        JSONArray a = (JSONArray) v;
        for (int i = 0; i < a.length(); i++) {
            Object r = a.opt(i);
            if (!(r instanceof JSONObject)) throw new Bad(field);
            JSONObject ro = (JSONObject) r;
            Object b = ro.opt("build");
            if (b != null && !(b instanceof String)) throw new Bad(field);
            List<String> hw = new ArrayList<String>();
            Object h = ro.opt("hardware");
            if (h != null) {
                if (!(h instanceof JSONArray)) throw new Bad(field);
                JSONArray ha = (JSONArray) h;
                for (int k = 0; k < ha.length(); k++) {
                    Object s = ha.opt(k);
                    if (!(s instanceof String)) throw new Bad(field);
                    hw.add((String) s);
                }
            }
            out.add(new CompatRule((String) b, hw));
        }
        return out;
    }
}
