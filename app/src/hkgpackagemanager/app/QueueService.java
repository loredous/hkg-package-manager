package hkgpackagemanager.app;

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import hkgpackagemanager.core.Queue;
import hkgpackagemanager.core.QueueCodec;
import hkgpackagemanager.core.QueueItem;
import hkgpackagemanager.core.QueueItems;
import hkgpackagemanager.core.QueueRunner;
import hkgpackagemanager.core.QueueSession;
import hkgpackagemanager.core.StickScanner;
import hkgpackagemanager.core.StickZip;
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
            if (!RUNNING.get()) stopSelf();
            return START_NOT_STICKY;
        }
        if (!RUNNING.compareAndSet(false, true)) {
            log.log("ignored " + action + ": a queue is already running");
            return START_NOT_STICKY;
        }
        startForeground(NOTIFICATION_ID, notification());
        final Context app = getApplicationContext();
        Thread t = new Thread(new Runnable() {
            public void run() { work(app, intent); }
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

    private void work(Context app, Intent intent) {
        AppLog log = AppLog.get(app);
        boolean show = false;
        try {
            show = dispatch(app, intent, log);
        } catch (Exception e) {
            log.log("queue worker failed: " + e);
            Queue q = current;
            if (q == null) q = new Queue(false, new ArrayList<QueueItem>());
            lastOutcome = new QueueRunner.Outcome(q, true, String.valueOf(e.getMessage()));
            show = true;
        } finally {
            RUNNING.set(false);
            broadcastChanged(app);
            if (show) {
                Intent open = new Intent(app, MainActivity.class);
                open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                open.putExtra(MainActivity.EXTRA_PROGRESS, true);
                app.startActivity(open);
            }
            stopSelf();
        }
    }

    /** Returns true when a run happened and the Progress screen should open. */
    private boolean dispatch(Context app, Intent intent, AppLog log) throws IOException {
        String action = intent.getAction();
        NemesisCli cli = new NemesisCli(app);
        if (ACTION_POSTINSTALL.equals(action)) {
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
            }
            return false;
        }
        String root = intent.getStringExtra(EXTRA_ROOT);
        if (root == null) {
            log.log("ignored " + action + ": no stick root");
            return false;
        }
        QueueSession s = session(app, cli, root);
        if (ACTION_START.equals(action)) {
            List<String> skipped = new ArrayList<String>();
            List<StickZip> scan = StickScanner.scan(new File(root));
            List<QueueItem> items = QueueItems.build(scan, intent.getStringArrayExtra(EXTRA_PATHS),
                    intent.getBooleanArrayExtra(EXTRA_UPDATES), skipped);
            for (String line : skipped) log.log("skipped " + line);
            if (items.isEmpty()) {
                log.log("nothing to install");
                return false;
            }
            return runOutcome(s.start(items));
        }
        if (ACTION_RESUME.equals(action)) {
            try {
                s.loadUnfinished();
            } catch (QueueCodec.Unreadable e) {
                log.log("unreadable install list on " + root + ": " + e.getMessage());
                return false;
            }
            return runOutcome(s.resume());
        }
        if (ACTION_DISCARD.equals(action)) {
            s.discard();
            broadcastChanged(app);
            return false;
        }
        log.log("ignored unknown action " + action);
        return false;
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
