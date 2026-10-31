package hkgpackagemanager.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import hkgpackagemanager.core.DescriptorSwap;
import hkgpackagemanager.core.QueueCodec;
import hkgpackagemanager.core.QueueSession;

/**
 * A stick was mounted: when it holds an unfinished queue and nothing is running, ask about it; when
 * it only holds our leftover resume descriptor, have the service remove it.
 */
public class MountReceiver extends BroadcastReceiver {
    @Override public void onReceive(final Context context, Intent intent) {
        if (intent == null || intent.getData() == null) return;
        final String path = intent.getData().getPath();
        if (!Sticks.isStickRoot(path) || QueueService.isRunning()) return;
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
        if (QueueService.isRunning()) return;
        if (!unfinished) {
            // our descriptor without an unfinished queue (an earlier clean-up failed): let the service
            // remove it (Ruling R11), or NEMESIS keeps asking about it
            if (DescriptorSwap.hasOurs(stick)) {
                log.log("leftover resume descriptor on " + path + "; removing it");
                context.startService(new Intent(context, QueueService.class)
                        .setAction(QueueService.ACTION_RESUME).putExtra(QueueService.EXTRA_ROOT, path));
            }
            return;
        }
        Intent open = new Intent(context, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        open.putExtra(MainActivity.EXTRA_ROOT, path);
        context.startActivity(open);
    }
}
