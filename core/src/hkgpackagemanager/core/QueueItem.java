package hkgpackagemanager.core;

/** One package in the multi-install queue. Immutable. */
public final class QueueItem {
    public final String relPath;
    public final long size;
    public final String sha256;
    public final String id;
    public final String version;
    public final boolean update;
    public final ItemState state;
    public final String message;

    public QueueItem(String relPath, long size, String sha256, String id, String version,
            boolean update, ItemState state, String message) {
        this.relPath = relPath;
        this.size = size;
        this.sha256 = sha256;
        this.id = id;
        this.version = version;
        this.update = update;
        this.state = state;
        this.message = message == null ? "" : message;
    }

    public QueueItem withState(ItemState s, String msg) {
        return new QueueItem(relPath, size, sha256, id, version, update, s, msg);
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof QueueItem)) return false;
        QueueItem b = (QueueItem) o;
        return size == b.size && update == b.update && state == b.state
                && eq(relPath, b.relPath) && eq(sha256, b.sha256) && eq(id, b.id)
                && eq(version, b.version) && eq(message, b.message);
    }

    @Override public int hashCode() {
        int h = relPath == null ? 0 : relPath.hashCode();
        h = 31 * h + (int) (size ^ (size >>> 32));
        h = 31 * h + (sha256 == null ? 0 : sha256.hashCode());
        h = 31 * h + (id == null ? 0 : id.hashCode());
        h = 31 * h + (version == null ? 0 : version.hashCode());
        h = 31 * h + (update ? 1 : 0);
        h = 31 * h + (state == null ? 0 : state.hashCode());
        return 31 * h + message.hashCode();
    }

    private static boolean eq(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }
}
