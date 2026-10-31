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

    @Override public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        Button pressed = Button.forAction(action);
        if (pressed == null) return;
        Settings s = new StateStore(context, new NemesisCli(context)).cached();
        if (s.button != pressed) return;
        boolean trigger;
        synchronized (ButtonReceiver.class) {
            if (counter == null || counterPresses != s.presses) {
                counter = new PressCounter(s.presses);
                counterPresses = s.presses;
            }
            trigger = counter.press(SystemClock.elapsedRealtime());
        }
        if (trigger) {
            Intent open = new Intent(context, MainActivity.class);
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(open);
        }
    }
}
