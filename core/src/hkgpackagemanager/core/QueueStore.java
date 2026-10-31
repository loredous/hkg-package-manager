package hkgpackagemanager.core;

import java.io.IOException;

/** Persists the queue after every state change. */
public interface QueueStore {
    void save(Queue q) throws IOException;
}
