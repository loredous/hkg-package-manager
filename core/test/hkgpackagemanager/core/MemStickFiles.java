package hkgpackagemanager.core;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

/** Map-backed StickFiles for tests; can simulate the stick being pulled. */
public final class MemStickFiles implements StickFiles {
    private static final Charset UTF8 = Charset.forName("UTF-8");
    private final Map<String, byte[]> files = new HashMap<String, byte[]>();
    private int writesLeft = -1;

    public void put(String rel, String text) { files.put(rel, text.getBytes(UTF8)); }

    public String text(String rel) {
        byte[] b = files.get(rel);
        return b == null ? null : new String(b, UTF8);
    }

    /** After n more successful writes, every write-type call throws. */
    public void failWritesAfter(int n) { writesLeft = n; }

    private void write() throws IOException {
        if (writesLeft == 0) throw new IOException("stick removed");
        if (writesLeft > 0) writesLeft--;
    }

    public boolean exists(String rel) { return files.containsKey(rel); }

    public byte[] read(String rel) throws IOException {
        byte[] b = files.get(rel);
        if (b == null) throw new FileNotFoundException(rel);
        return b.clone();
    }

    public void writeAtomic(String rel, byte[] data) throws IOException {
        write();
        files.put(rel, data.clone());
    }

    public void move(String fromRel, String toRel) throws IOException {
        write();
        byte[] b = files.remove(fromRel);
        if (b == null) throw new FileNotFoundException(fromRel);
        files.put(toRel, b);
    }

    public void delete(String rel) throws IOException {
        write();
        files.remove(rel);
    }
}
