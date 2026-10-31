package hkgpackagemanager.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** View and threading helpers shared by the screens: big text, big targets, work off the UI thread. */
final class Ui {
    static final int TEXT_SP = 24;
    static final int TARGET_DP = 64;
    static final int GREY = 0xFF888888;
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

    static void info(Activity a, String title, String message) {
        confirm(a, title, message, "OK", null, null, null);
    }

    /** A dialog with big text. {@code no} may be null for a single-button dialog; cancelling does nothing unless {@code onCancel} is given. */
    static void confirm(Activity a, String title, String message, String yes, Runnable onYes,
            String no, Runnable onNo) {
        confirm(a, title, message, yes, onYes, no, onNo, null);
    }

    /** As above; Back or a tap outside runs only {@code onCancel} (never {@code onNo}, so cancelling can't discard). */
    static void confirm(Activity a, String title, String message, String yes, final Runnable onYes,
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
        for (int which : new int[] {DialogInterface.BUTTON_POSITIVE, DialogInterface.BUTTON_NEGATIVE}) {
            Button btn = d.getButton(which);
            if (btn != null) {
                btn.setTextSize(TEXT_SP);
                btn.setMinHeight(dp(a, TARGET_DP));
            }
        }
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
