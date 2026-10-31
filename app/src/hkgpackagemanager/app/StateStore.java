package hkgpackagemanager.app;

import android.content.Context;
import hkgpackagemanager.core.CliResult;
import hkgpackagemanager.core.Nemesis;
import hkgpackagemanager.core.NemesisCalls;
import hkgpackagemanager.core.Settings;
import hkgpackagemanager.core.SettingsCodec;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Settings kept under /data/nemesis (survives app reinstall), with a cache in filesDir for the receiver. */
public final class StateStore {
    static final String STATE_DIR = "/data/nemesis/hkg.package.manager";
    static final String SETTINGS_PATH = STATE_DIR + "/settings.txt";

    private final File cache;
    private final File stage;
    private final Nemesis nemesis;
    private final AppLog log;

    public StateStore(Context c, Nemesis n) {
        File dir = c.getFilesDir();
        this.cache = new File(dir, "settings.txt");
        this.stage = new File(dir, "stage/settings.txt");
        this.nemesis = n;
        this.log = AppLog.get(c);
    }

    /** The root copy if readable (refreshing the cache), else the cache, else the defaults. */
    public Settings load() {
        CliResult r = nemesis.root(NemesisCalls.catCommand(SETTINGS_PATH));
        if (r.exit == 0 && r.output.trim().length() > 0) {
            try {
                writeFile(cache, r.output.getBytes("UTF-8"));
            } catch (IOException e) {
                log.log("cannot refresh settings cache: " + e.getMessage());
            }
            return SettingsCodec.decode(r.output);
        }
        return cached();
    }

    /** Writes the cache, then root-copies it into the state folder. */
    public void save(Settings s) {
        byte[] data;
        try {
            data = SettingsCodec.encode(s).getBytes("UTF-8");
            writeFile(cache, data);
        } catch (IOException e) {
            log.log("cannot write settings cache: " + e.getMessage());
            return;
        }
        try {
            writeFile(stage, data);
            CliResult r = nemesis.root(NemesisCalls.copyIntoCommand(stage.getAbsolutePath(), SETTINGS_PATH));
            if (r.exit != 0) log.log("settings root copy failed: " + r.lastLine());
        } catch (IOException e) {
            log.log("cannot stage settings: " + e.getMessage());
        } finally {
            stage.delete();
        }
    }

    /** The cache only; never touches the CLI. */
    public Settings cached() {
        if (!cache.isFile()) return Settings.DEFAULT;
        try {
            InputStream in = new FileInputStream(cache);
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[1024];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                return SettingsCodec.decode(new String(out.toByteArray(), "UTF-8"));
            } finally {
                in.close();
            }
        } catch (IOException e) {
            return Settings.DEFAULT;
        }
    }

    private static void writeFile(File f, byte[] data) throws IOException {
        File dir = f.getParentFile();
        if (dir != null) dir.mkdirs();
        FileOutputStream out = new FileOutputStream(f);
        try {
            out.write(data);
        } finally {
            out.close();
        }
    }
}
