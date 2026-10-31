package hkgpackagemanager.app;

import android.content.Context;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Timestamped log in filesDir/log.txt, rotated to log.1.txt past 256 KiB. Never log file contents. */
public final class AppLog {
    static final long MAX_BYTES = 256 * 1024;
    private static AppLog instance;

    private final File log;
    private final File old;
    private final SimpleDateFormat stamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

    private AppLog(File dir) {
        log = new File(dir, "log.txt");
        old = new File(dir, "log.1.txt");
    }

    public static synchronized AppLog get(Context c) {
        if (instance == null) instance = new AppLog(c.getApplicationContext().getFilesDir());
        return instance;
    }

    public synchronized void log(String line) {
        try {
            if (log.length() > MAX_BYTES) {
                old.delete();
                log.renameTo(old);
            }
            FileOutputStream out = new FileOutputStream(log, true);
            try {
                out.write((stamp.format(new Date()) + " " + line + "\n").getBytes("UTF-8"));
            } finally {
                out.close();
            }
        } catch (IOException e) {
            // logging must never break the caller
        }
    }

    /** log.1.txt followed by log.txt. */
    public synchronized byte[] snapshot() {
        ByteArrayOutputStream all = new ByteArrayOutputStream();
        append(old, all);
        append(log, all);
        return all.toByteArray();
    }

    private static void append(File f, ByteArrayOutputStream to) {
        if (!f.isFile()) return;
        try {
            InputStream in = new FileInputStream(f);
            try {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) to.write(buf, 0, n);
            } finally {
                in.close();
            }
        } catch (IOException e) {
            // skip an unreadable file
        }
    }
}
