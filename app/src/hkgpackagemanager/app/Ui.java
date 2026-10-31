package hkgpackagemanager.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import java.util.List;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** View and threading helpers shared by the screens: big text, big targets, work off the UI thread. */
final class Ui {
    static final int TEXT_SP = 24;
    static final int TARGET_DP = 64;
    static final int GREY = 0xFF888888;
    static final int GREEN = 0xFF2E9E44;
    static final int YELLOW = 0xFFC9A100;
    static final int RED = 0xFFD03030;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /** One tab's content. */
    interface Screen {
        View view();
        /** Reload whatever the screen shows; called when its tab opens. */
        void refresh();
    }

    interface Work<T> { T run() throws Exception; }

    /** Gets the result on the UI thread; null when the work threw (already logged). */
    interface Done<T> { void done(T result); }

    private Ui() {}

    static int dp(Context c, int dp) {
        return Math.round(dp * c.getResources().getDisplayMetrics().density);
    }

    /** Runs {@code work} on a thread, then {@code done} on the UI thread unless the activity is gone. */
    static <T> void background(final Activity a, final Work<T> work, final Done<T> done) {
        new Thread(new Runnable() {
            public void run() {
                T result = null;
                try {
                    result = work.run();
                } catch (Exception e) {
                    AppLog.get(a).log("background work failed: " + e);
                }
                final T r = result;
                MAIN.post(new Runnable() {
                    public void run() {
                        if (alive(a)) done.done(r);
                    }
                });
            }
        }, "hkgpm-ui").start();
    }

    /** False once the activity is finishing or destroyed (a recreation destroys it without finishing). */
    static boolean alive(Activity a) {
        return !a.isFinishing() && !a.isDestroyed();
    }

    /** Dismisses a dialog if it is still showing; never throws. */
    static void dismiss(android.app.Dialog d) {
        try {
            if (d.isShowing()) d.dismiss();
        } catch (RuntimeException e) {
            // its window is already gone
        }
    }

    static TextView text(Context c, String s) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TEXT_SP);
        int p = dp(c, 8);
        t.setPadding(p, p, p, p);
        return t;
    }

    static TextView bold(Context c, String s) {
        TextView t = text(c, s);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    static Button button(Context c, String label, View.OnClickListener l) {
        Button b = new Button(c);
        b.setText(label);
        b.setTextSize(TEXT_SP);
        b.setMinHeight(dp(c, TARGET_DP));
        b.setMinimumHeight(dp(c, TARGET_DP));
        b.setMinWidth(dp(c, TARGET_DP));
        b.setOnClickListener(l);
        return b;
    }

    static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        return l;
    }

    /** A scrolling vertical column; the returned scroll view is the screen's view. */
    static ScrollView scroll(Context c, LinearLayout content) {
        ScrollView s = new ScrollView(c);
        s.addView(content);
        return s;
    }

    static AlertDialog info(Activity a, String title, String message) {
        return confirm(a, title, message, "OK", null, null, null);
    }

    /** A dialog with big text. {@code no} may be null for a single-button dialog; cancelling does nothing unless {@code onCancel} is given. */
    static AlertDialog confirm(Activity a, String title, String message, String yes, Runnable onYes,
            String no, Runnable onNo) {
        return confirm(a, title, message, yes, onYes, no, onNo, null);
    }

    /** As above; Back or a tap outside runs only {@code onCancel} (never {@code onNo}, so cancelling can't discard). */
    static AlertDialog confirm(Activity a, String title, String message, String yes, final Runnable onYes,
            String no, final Runnable onNo, final Runnable onCancel) {
        AlertDialog.Builder b = new AlertDialog.Builder(a);
        if (title != null) b.setTitle(title);
        b.setView(scroll(a, wrap(a, message)));
        b.setPositiveButton(yes, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int w) { if (onYes != null) onYes.run(); }
        });
        if (no != null) {
            b.setNegativeButton(no, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) { if (onNo != null) onNo.run(); }
            });
        }
        b.setOnCancelListener(new DialogInterface.OnCancelListener() {
            public void onCancel(DialogInterface d) { if (onCancel != null) onCancel.run(); }
        });
        AlertDialog d = b.create();
        d.show();
        styleButtons(a, d);
        return d;
    }

    private static void styleButtons(Activity a, AlertDialog d) {
        for (int which : new int[] {DialogInterface.BUTTON_POSITIVE, DialogInterface.BUTTON_NEGATIVE}) {
            Button btn = d.getButton(which);
            if (btn != null) {
                btn.setTextSize(TEXT_SP);
                btn.setMinHeight(dp(a, TARGET_DP));
            }
        }
    }

    /** A confirmation whose Install button stays disabled until the one tick is ticked; Back or Cancel do nothing. */
    static AlertDialog confirmWithTick(Activity a, String message, String tick, String yes, Runnable onYes) {
        List<String> ticks = new java.util.ArrayList<String>();
        ticks.add(tick);
        return confirmWithTicks(a, message, ticks, yes, onYes);
    }

    /** As {@link #confirmWithTick}, with one tick per label; the positive button needs all of them. */
    static AlertDialog confirmWithTicks(Activity a, String message, List<String> ticks, String yes,
            final Runnable onYes) {
        LinearLayout body = wrap(a, message);
        final int[] left = {ticks.size()};
        final AlertDialog[] dialog = new AlertDialog[1];
        for (String label : ticks) {
            CheckBox box = new CheckBox(a);
            box.setText(label);
            box.setTextSize(TEXT_SP);
            box.setMinimumHeight(dp(a, TARGET_DP));
            box.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                public void onCheckedChanged(CompoundButton b, boolean on) {
                    left[0] += on ? -1 : 1;
                    dialog[0].getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(left[0] == 0);
                }
            });
            body.addView(box);
        }
        AlertDialog.Builder b = new AlertDialog.Builder(a);
        b.setView(scroll(a, body));
        b.setPositiveButton(yes, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int w) { if (onYes != null) onYes.run(); }
        });
        b.setNegativeButton("Cancel", null);
        AlertDialog d = b.create();
        dialog[0] = d;
        d.show();
        styleButtons(a, d);
        d.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(left[0] == 0);
        return d;
    }

    /** A dialog with no buttons, to dismiss when the work is done. */
    static AlertDialog busy(Activity a, String message) {
        AlertDialog d = new AlertDialog.Builder(a).setView(wrap(a, message)).setCancelable(false).create();
        d.show();
        return d;
    }

    private static LinearLayout wrap(Context c, String message) {
        LinearLayout l = column(c);
        l.addView(text(c, message));
        return l;
    }
}
