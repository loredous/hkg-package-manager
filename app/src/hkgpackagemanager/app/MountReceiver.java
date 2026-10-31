package hkgpackagemanager.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import hkgpackagemanager.core.QueueCodec;
import hkgpackagemanager.core.QueueSession;

/** A stick was mounted: when it holds an unfinished queue and nothing is running, ask about it. */
public class MountReceiver extends BroadcastReceiver {
    @Override public void onReceive(final Context context, Intent intent) {
        if (intent == null || intent.getData() == null) return;
        final String path = intent.getData().getPath();
        boolean isStick = false;
        for (int i = 0; i <= 2; i++) {
            if (("/storage/usb" + i).equals(path)) isStick = true;
        }
        if (!isStick || QueueService.isRunning()) return;
        final PendingResult pending = goAsync();
        new Thread(new Runnable() {
            public void run() {
                try {
                    check(context, path);
                } catch (RuntimeException e) {
                    AppLog.get(context).log("mount check failed: " + e);
                } finally {
                    pending.finish();
                }
            }
        }, "hkgpm-mount").start();
    }

    private static void check(Context context, String path) {
        AppLog log = AppLog.get(context);
        NemesisCli cli = new NemesisCli(context);
        StickFs stick = new StickFs(context, path, cli);
        QueueSession session = new QueueSession(stick, path, cli,
                stick, new byte[0], null);
        boolean unfinished;
        try {
            unfinished = session.loadUnfinished() != null;
        } catch (QueueCodec.Unreadable e) {
            log.log("unreadable install list on " + path + ": " + e.getMessage());
            unfinished = true; // the screen explains it and offers to discard
        }
        if (!unfinished || QueueService.isRunning()) return;
        Intent open = new Intent(context, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        open.putExtra(MainActivity.EXTRA_RESUME, true);
        open.putExtra(MainActivity.EXTRA_ROOT, path);
        context.startActivity(open);
    }
}
