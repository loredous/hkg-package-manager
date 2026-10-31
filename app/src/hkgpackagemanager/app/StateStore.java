package hkgpackagemanager.app;

import android.content.Context;
import hkgpackagemanager.core.CliResult;
import hkgpackagemanager.core.Nemesis;
import hkgpackagemanager.core.NemesisCalls;
import hkgpackagemanager.core.Settings;
import hkgpackagemanager.core.SettingsCodec;
import hkgpackagemanager.core.TrustedKeys;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Settings kept under /data/nemesis (survives app reinstall), with a cache in filesDir for the receiver. */
public final class StateStore {
    private static final Object KEYS_LOCK = new Object();
    static final String STATE_DIR = "/data/nemesis/hkg.package.manager";
    static final String SETTINGS_PATH = STATE_DIR + "/settings.txt";
    static final String KEYS_PATH = STATE_DIR + "/keys.txt";

    private final File cache;
    private final File stage;
    private final File keysCache;
    private final File keysStage;
    private final Nemesis nemesis;
    private final AppLog log;

    public StateStore(Context c, Nemesis n) {
        File dir = c.getFilesDir();
        this.cache = new File(dir, "settings.txt");
        this.stage = new File(dir, "stage/settings.txt");
        this.keysCache = new File(dir, "keys.txt");
        this.keysStage = new File(dir, "stage/keys.txt");
        this.nemesis = n;
        this.log = AppLog.get(c);
    }

    /** The root copy if readable (refreshing the cache), else the cache, else the defaults. */
    public Settings load() {
        String text = loadText(SETTINGS_PATH, cache, "settings");
        return text == null ? cached() : SettingsCodec.decode(text);
    }

    /** Writes the cache, then root-copies it into the state folder. */
    public void save(Settings s) {
        saveText(SettingsCodec.encode(s), SETTINGS_PATH, cache, stage, "settings");
    }

    /** The root copy if readable (refreshing the cache), else the cache, else no keys. */
    public TrustedKeys loadKeys() {
        String text = loadText(KEYS_PATH, keysCache, "keys");
        if (text == null) text = readCache(keysCache);
        return text == null ? TrustedKeys.NONE : TrustedKeys.decode(text);
    }

    /** Reads the keys fresh, adds one and saves, all under one lock; returns the new set, or null if it couldn't be saved. */
    public TrustedKeys addKey(String fp, String label) {
        synchronized (KEYS_LOCK) {
            String text = keysTextForChange();
            if (text == null) return null;
            TrustedKeys k = TrustedKeys.decode(text).with(fp, label);
            return saveKeys(k) ? k : null;
        }
    }

    /** Reads the keys fresh, removes one and saves, all under one lock; returns the new set, or null if it couldn't be saved. */
    public TrustedKeys removeKey(String fp) {
        synchronized (KEYS_LOCK) {
            String text = keysTextForChange();
            if (text == null) return null;
            TrustedKeys k = TrustedKeys.decode(text).without(fp);
            return saveKeys(k) ? k : null;
        }
    }

    /**
     * The keys text to build a change on: the root copy, or the cache (or nothing) when the file is
     * missing or empty. Null when the root read failed for another reason and there is no cache, so
     * a transient failure can't wipe the trusted keys.
     */
    private String keysTextForChange() {
        CliResult r = nemesis.root(NemesisCalls.catCommand(KEYS_PATH));
        if (r.exit == 0 && r.output.trim().length() > 0) {
            try {
                writeFile(keysCache, r.output.getBytes("UTF-8"));
            } catch (IOException e) {
                log.log("cannot refresh keys cache: " + e.getMessage());
            }
            return r.output;
        }
        String cached = readCache(keysCache);
        boolean missing = r.exit == 0 || r.output.toLowerCase(java.util.Locale.US).contains("no such file");
        if (cached != null) return cached;
        return missing ? "" : null;
    }

    /** Writes the cache, then root-copies it into the state folder; false if either step failed. */
    public boolean saveKeys(TrustedKeys k) {
        return saveText(k.encode(), KEYS_PATH, keysCache, keysStage, "keys");
    }

    /** The root file's text (refreshing the cache), or null if it is missing or empty. */
    private String loadText(String rootPath, File cacheFile, String what) {
        CliResult r = nemesis.root(NemesisCalls.catCommand(rootPath));
        if (r.exit == 0 && r.output.trim().length() > 0) {
            try {
                writeFile(cacheFile, r.output.getBytes("UTF-8"));
            } catch (IOException e) {
                log.log("cannot refresh " + what + " cache: " + e.getMessage());
            }
            return r.output;
        }
        return null;
    }

    private boolean saveText(String text, String rootPath, File cacheFile, File stageFile, String what) {
        byte[] data;
        try {
            data = text.getBytes("UTF-8");
            writeFile(cacheFile, data);
        } catch (IOException e) {
            log.log("cannot write " + what + " cache: " + e.getMessage());
            return false;
        }
        try {
            writeFile(stageFile, data);
            CliResult r = nemesis.root(NemesisCalls.copyIntoCommand(stageFile.getAbsolutePath(), rootPath));
            if (r.exit != 0) log.log(what + " root copy failed: " + r.lastLine());
            return r.exit == 0;
        } catch (IOException e) {
            log.log("cannot stage " + what + ": " + e.getMessage());
            return false;
        } finally {
            stageFile.delete();
        }
    }

    /** False after a NEMESIS update wiped filesDir (it uninstalls without -k) until load() refreshes it. */
    public boolean hasCache() { return cache.isFile(); }

    /** The cache only; never touches the CLI. */
    public Settings cached() {
        String text = readCache(cache);
        return text == null ? Settings.DEFAULT : SettingsCodec.decode(text);
    }

    private static String readCache(File f) {
        if (!f.isFile()) return null;
        try {
            InputStream in = new FileInputStream(f);
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[1024];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                return new String(out.toByteArray(), "UTF-8");
            } finally {
                in.close();
            }
        } catch (IOException e) {
            return null;
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
