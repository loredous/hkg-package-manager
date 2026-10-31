package hkgpackagemanager.app;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import hkgpackagemanager.core.CatalogReader;
import hkgpackagemanager.core.CatalogView;
import hkgpackagemanager.core.CatalogZips;
import hkgpackagemanager.core.FileFingerprint;
import hkgpackagemanager.core.ItemState;
import hkgpackagemanager.core.Queue;
import hkgpackagemanager.core.QueueCodec;
import hkgpackagemanager.core.QueueItem;
import hkgpackagemanager.core.QueueItems;
import hkgpackagemanager.core.QueueRunner;
import hkgpackagemanager.core.QueueSession;
import hkgpackagemanager.core.StickScanner;
import hkgpackagemanager.core.StickZip;
import hkgpackagemanager.core.ZipFingerprint;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Runs the install queue on a worker thread in a foreground service, so it outlives the screens. */
public class QueueService extends Service {
    public static final String ACTION_START = "hkgpackagemanager.app.action.START";
    public static final String ACTION_RESUME = "hkgpackagemanager.app.action.RESUME";
    public static final String ACTION_DISCARD = "hkgpackagemanager.app.action.DISCARD";
    public static final String ACTION_POSTINSTALL = "hkgpackagemanager.app.action.POSTINSTALL";
    public static final String ACTION_CHANGED = "hkgpackagemanager.app.action.CHANGED";
    public static final String EXTRA_ROOT = "root";
    public static final String EXTRA_PATHS = "paths";
    public static final String EXTRA_UPDATES = "updates";

    private static final int NOTIFICATION_ID = 1;
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    private static final Object LOCK = new Object();
    private static int lastStartId; // guarded by LOCK
    private static volatile Queue current;
    private static volatile QueueRunner.Outcome lastOutcome;

    public static boolean isRunning() { return RUNNING.get(); }
    public static Queue current() { return current; }
    public static QueueRunner.Outcome lastOutcome() { return lastOutcome; }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(final Intent intent, int flags, int startId) {
        AppLog log = AppLog.get(this);
        String action = intent == null ? null : intent.getAction();
        if (action == null) {
            // restarted without a request; nothing to do
            synchronized (LOCK) {
                lastStartId = startId;
                if (!RUNNING.get()) stopSelfResult(startId);
            }
            return START_NOT_STICKY;
        }
        final Queue prevQueue;
        final QueueRunner.Outcome prevOutcome;
        synchronized (LOCK) {
            lastStartId = startId;
            if (!RUNNING.compareAndSet(false, true)) {
                log.log("ignored " + action + ": a queue is already running");
                return START_NOT_STICKY;
            }
            prevQueue = current;
            prevOutcome = lastOutcome;
            current = null;
            lastOutcome = null;
        }
        startForeground(NOTIFICATION_ID, notification());
        final Context app = getApplicationContext();
        Thread t = new Thread(new Runnable() {
            public void run() { work(app, intent, prevQueue, prevOutcome); }
        }, "hkgpm-queue");
        t.start();
        return START_NOT_STICKY;
    }

    private Notification notification() {
        return new Notification.Builder(this)
                .setContentTitle("HKG Package Manager")
                .setContentText("Installing packages…")
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setOngoing(true)
                .build();
    }

    private void work(Context app, Intent intent, Queue prevQueue, QueueRunner.Outcome prevOutcome) {
        AppLog log = AppLog.get(app);
        boolean show = false;
        try {
            show = dispatch(app, intent, log);
            if (!show && current == null && lastOutcome == null) {
                // nothing ran (e.g. a resume that only tidied a leftover descriptor): keep the last report
                current = prevQueue;
                lastOutcome = prevOutcome;
            }
        } catch (Exception e) {
            log.log("queue worker failed: " + e);
            Queue q = current;
            if (q == null) q = new Queue(false, new ArrayList<QueueItem>());
            lastOutcome = new QueueRunner.Outcome(q, true, e.getMessage() != null ? e.getMessage() : e.toString());
            show = true;
        } finally {
            // first, under the lock, so a request accepted after RUNNING clears can't be stopped mid-run:
            // stopSelfResult is a no-op when a newer start has been delivered or is queued
            synchronized (LOCK) {
                RUNNING.set(false);
                stopSelfResult(lastStartId);
            }
            try {
                broadcastChanged(app);
            } catch (RuntimeException e) {
                log.log("final broadcast failed: " + e);
            }
            if (show) {
                try {
                    Intent open = new Intent(app, MainActivity.class);
                    open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    open.putExtra(MainActivity.EXTRA_PROGRESS, true);
                    app.startActivity(open);
                } catch (RuntimeException e) {
                    log.log("cannot open the progress screen: " + e);
                }
            }
        }
    }

    /** Returns true when a run happened and the Progress screen should open. */
    private boolean dispatch(Context app, Intent intent, AppLog log) throws IOException {
        String action = intent.getAction();
        NemesisCli cli = new NemesisCli(app);
        if (ACTION_POSTINSTALL.equals(action)) {
            // a NEMESIS update uninstalls first (pm uninstall without -k), which wipes the settings cache in
            // filesDir; refresh it from the state folder so the car button keeps its setting
            try {
                new StateStore(app, cli).load();
            } catch (RuntimeException e) {
                log.log("cannot refresh settings: " + e);
            }
            return sweep(app, cli, log);
        }
        String root = intent.getStringExtra(EXTRA_ROOT);
        if (root == null) {
            if (ACTION_RESUME.equals(action)) return sweep(app, cli, log);
            log.log("ignored " + action + ": no stick root");
            return false;
        }
        QueueSession s = session(app, cli, root);
        if (ACTION_START.equals(action)) {
            List<CatalogView> views = CatalogReader.read(new File(root), new StateStore(app, cli).loadKeys(),
                    new CatalogReader.Fingerprinter() {
                        @Override public ZipFingerprint of(File f) throws IOException {
                            return FileFingerprint.of(f);
                        }
                    });
            List<StickZip> scan = new ArrayList<StickZip>(StickScanner.scan(new File(root)));
            scan.addAll(CatalogZips.zips(views));
            List<QueueItem> items = QueueItems.build(scan, CatalogZips.expected(views),
                    intent.getStringArrayExtra(EXTRA_PATHS), intent.getBooleanArrayExtra(EXTRA_UPDATES));
            for (QueueItem i : items) {
                if (i.state != ItemState.WAITING) log.log("not queued: " + i.relPath + ": " + i.message);
            }
            if (items.isEmpty()) {
                log.log("nothing to install");
                return false;
            }
            return runOutcome(s.start(items));
        }
        if (ACTION_RESUME.equals(action)) {
            Queue q;
            try {
                q = s.loadUnfinished();
            } catch (QueueCodec.Unreadable e) {
                log.log("unreadable install list on " + root + ": " + e.getMessage());
                return false;
            }
            if (q == null) {
                tidy(s, root, log);
                return false;
            }
            return runOutcome(s.resume());
        }
        if (ACTION_DISCARD.equals(action)) {
            if (s.discard()) {
                // keep the R20 note for the Progress screen: NEMESIS may now ask about the restored descriptor
                Queue q = current;
                lastOutcome = new QueueRunner.Outcome(q != null ? q : new Queue(false, new ArrayList<QueueItem>()),
                        false, null, true);
            }
            broadcastChanged(app);
            return false;
        }
        log.log("ignored unknown action " + action);
        return false;
    }

    /**
     * Every mounted stick: continues the first unfinished queue found; on sticks without one, removes a
     * leftover resume descriptor (Ruling R11). Returns true when a run happened.
     */
    private boolean sweep(Context app, NemesisCli cli, AppLog log) throws IOException {
        for (String root : Sticks.mounted()) {
            QueueSession s = session(app, cli, root);
            Queue q;
            try {
                q = s.loadUnfinished();
            } catch (QueueCodec.Unreadable e) {
                log.log("unreadable install list on " + root + ": " + e.getMessage());
                continue;
            }
            if (q != null) return runOutcome(s.resume());
            tidy(s, root, log);
        }
        return false;
    }

    /** No unfinished queue on this stick: resume() then only removes our leftover descriptor, if any. */
    private static void tidy(QueueSession s, String root, AppLog log) {
        try {
            if (s.resume() != null) log.log("unexpected run while tidying " + root);
        } catch (IOException e) {
            log.log("cannot remove the leftover resume descriptor on " + root + ": " + e.getMessage());
        }
    }

    private boolean runOutcome(QueueRunner.Outcome o) {
        if (o == null) return false;
        lastOutcome = o;
        current = o.queue;
        return true;
    }

    private QueueSession session(final Context app, NemesisCli cli, String root) throws IOException {
        StickFs fs = new StickFs(app, root, cli);
        return new QueueSession(fs, root, cli, fs, resumeScript(app), new QueueRunner.Listener() {
            public void changed(Queue q) {
                current = q;
                broadcastChanged(app);
            }
        });
    }

    private static byte[] resumeScript(Context app) throws IOException {
        InputStream in = app.getAssets().open("resume.sh");
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toByteArray();
        } finally {
            in.close();
        }
    }

    private static void broadcastChanged(Context app) {
        Intent i = new Intent(ACTION_CHANGED);
        i.setPackage(app.getPackageName());
        app.sendBroadcast(i);
    }
}
