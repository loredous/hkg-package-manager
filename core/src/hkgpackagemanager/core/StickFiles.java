package hkgpackagemanager.core;

import java.io.IOException;

/** The files on the USB stick; every path is relative to the stick root. */
public interface StickFiles {
    boolean exists(String rel);
    byte[] read(String rel) throws IOException;
    void writeAtomic(String rel, byte[] data) throws IOException;
    void move(String fromRel, String toRel) throws IOException;
    void delete(String rel) throws IOException;
}
