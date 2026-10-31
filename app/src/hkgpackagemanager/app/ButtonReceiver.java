package hkgpackagemanager.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import hkgpackagemanager.core.Button;
import hkgpackagemanager.core.PressCounter;
import hkgpackagemanager.core.Settings;

/** Opens the manager after N presses of the chosen car button. */
public class ButtonReceiver extends BroadcastReceiver {
    private static PressCounter counter;
    private static int counterPresses = -1;
    /** When the cache is missing, at most one root read per 30 s (each press would otherwise spawn one). */
    private static final long RETRY_MS = 30000;
    private static long lastLoad = -RETRY_MS; // guarded by ButtonReceiver.class

    @Override public void onReceive(final Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        final Button pressed = Button.forAction(action);
        if (pressed == null) return;
        final long now = SystemClock.elapsedRealtime();
        final StateStore store = new StateStore(context, new NemesisCli(context));
        boolean load;
        synchronized (ButtonReceiver.class) {
            load = !store.hasCache() && now - lastLoad >= RETRY_MS;
            if (load) lastLoad = now;
        }
        if (!load) {
            evaluate(context, store.cached(), pressed, now);
            return;
        }
        // no cache: a self-update wiped filesDir. Read the settings from the state folder off the main
        // thread (load() refreshes the cache and falls back to the defaults only when that read fails).
        final PendingResult pending = goAsync();
        new Thread(new Runnable() {
            public void run() {
                try {
                    evaluate(context, store.load(), pressed, now);
                } catch (RuntimeException e) {
                    AppLog.get(context).log("button settings load failed: " + e);
                } finally {
                    pending.finish();
                }
            }
        }, "hkgpm-button").start();
    }

    private static void evaluate(Context context, Settings s, Button pressed, long now) {
        if (s.button != pressed) return;
        boolean trigger;
        synchronized (ButtonReceiver.class) {
            if (counter == null || counterPresses != s.presses) {
                counter = new PressCounter(s.presses);
                counterPresses = s.presses;
            }
            trigger = counter.press(now);
        }
        if (trigger) {
            Intent open = new Intent(context, MainActivity.class);
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(open);
        }
    }
}
