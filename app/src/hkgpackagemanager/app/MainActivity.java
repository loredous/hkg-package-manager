package hkgpackagemanager.app;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import hkgpackagemanager.core.Queue;
import hkgpackagemanager.core.QueueCodec;
import hkgpackagemanager.core.QueueItem;
import hkgpackagemanager.core.QueueSession;
import hkgpackagemanager.core.ScreenText;

/** The four tabs, and the start-up decision: progress, an unfinished install, or the USB drive. */
public class MainActivity extends Activity {
    /** boolean: an unfinished queue exists and the resume script wants it continued without asking. */
    public static final String EXTRA_RESUME = "resume";
    /** String: the stick root the unfinished queue is on (ignored unless it is a known stick root). */
    public static final String EXTRA_ROOT = "root";
    /** boolean: show the Progress screen (the service just finished or stopped a run). */
    public static final String EXTRA_PROGRESS = "progress";

    static final int TAB_DRIVE = 0;
    static final int TAB_INSTALLED = 1;
    static final int TAB_PROGRESS = 2;
    private static final String[] TAB_NAMES = {"USB drive", "Installed", "Progress", "Settings"};

    private final Button[] tabs = new Button[TAB_NAMES.length];
    private Ui.Screen[] screens;
    private FrameLayout frame;
    private int current = -1;
    private NemesisCli cli;

    private final BroadcastReceiver changed = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (current >= 0 && (current == TAB_PROGRESS || !QueueService.isRunning())) screens[current].refresh();
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        cli = new NemesisCli(this);
        screens = new Ui.Screen[] {new DriveScreen(this, cli), new InstalledScreen(this, cli),
                new ProgressScreen(this), new SettingsScreen(this, cli)};
        LinearLayout page = Ui.column(this);
        LinearLayout bar = Ui.row(this);
        for (int i = 0; i < tabs.length; i++) {
            final int index = i;
            tabs[i] = Ui.button(this, TAB_NAMES[i], new View.OnClickListener() {
                public void onClick(View v) { show(index); }
            });
            bar.addView(tabs[i], new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        }
        frame = new FrameLayout(this);
        page.addView(bar);
        page.addView(frame, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(page);
        route(getIntent());
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        route(intent);
    }

    @Override protected void onResume() {
        super.onResume();
        registerReceiver(changed, new IntentFilter(QueueService.ACTION_CHANGED));
        if (current >= 0) screens[current].refresh(); // something may have changed while we were away
    }

    @Override protected void onPause() {
        unregisterReceiver(changed);
        super.onPause();
    }

    void show(int tab) {
        current = tab;
        frame.removeAllViews();
        frame.addView(screens[tab].view());
        for (int i = 0; i < tabs.length; i++) tabs[i].setEnabled(i != tab);
        screens[tab].refresh();
    }

    void startQueue(String action, String root) {
        Intent i = new Intent(this, QueueService.class).setAction(action);
        if (root != null) i.putExtra(QueueService.EXTRA_ROOT, root);
        startService(i);
    }

    /** What the start-up check found on one stick. */
    private static final class Found {
        String root;
        Queue queue;      // readable unfinished queue
        String unreadable; // or the reason it cannot be read
    }

    private void route(Intent in) {
        String root = in.getStringExtra(EXTRA_ROOT);
        if (!Sticks.isStickRoot(root)) root = null; // we are exported; only trust known mount points
        final boolean resume = in.getBooleanExtra(EXTRA_RESUME, false);
        boolean progress = in.getBooleanExtra(EXTRA_PROGRESS, false);
        if (QueueService.isRunning() || progress) {
            show(TAB_PROGRESS);
            return;
        }
        final String first = root;
        Ui.background(this, new Ui.Work<Found>() {
            public Found run() {
                for (String r : ScreenText.preferredFirst(Sticks.mounted(), first)) {
                    StickFs fs = new StickFs(MainActivity.this, r, cli);
                    QueueSession s = new QueueSession(fs, r, cli, fs, new byte[0], null);
                    Found f = new Found();
                    f.root = r;
                    try {
                        f.queue = s.loadUnfinished();
                        if (f.queue != null) return f;
                    } catch (QueueCodec.Unreadable e) {
                        f.unreadable = String.valueOf(e.getMessage());
                        return f;
                    }
                }
                return new Found();
            }
        }, new Ui.Done<Found>() {
            public void done(Found f) {
                if (QueueService.isRunning()) show(TAB_PROGRESS);
                else if (f == null || f.root == null) show(TAB_DRIVE);
                else if (f.unreadable != null) askUnreadable(f);
                else if (resume) {
                    startQueue(QueueService.ACTION_RESUME, f.root);
                    show(TAB_PROGRESS);
                } else askResume(f);
            }
        });
    }

    private void askUnreadable(final Found f) {
        Ui.confirm(this, null, "Couldn't read the install list on " + f.root + ": " + f.unreadable,
                "Discard", new Runnable() {
                    public void run() { discard(f.root); }
                }, "Keep", new Runnable() {
                    public void run() { show(TAB_DRIVE); }
                }, new Runnable() {
                    public void run() { show(TAB_DRIVE); }
                });
    }

    private void askResume(final Found f) {
        StringBuilder sb = new StringBuilder(ScreenText.RESUME_PROMPT).append("\n");
        for (QueueItem i : f.queue.items) {
            sb.append("\n").append(ScreenText.itemLine(i, i.id));
        }
        Ui.confirm(this, null, sb.toString(), "Continue", new Runnable() {
            public void run() {
                startQueue(QueueService.ACTION_RESUME, f.root);
                show(TAB_PROGRESS);
            }
        }, "Discard", new Runnable() {
            public void run() { discard(f.root); }
        }, new Runnable() {
            public void run() { show(TAB_DRIVE); } // Back or an outside tap keeps the list
        });
    }

    private void discard(String root) {
        startQueue(QueueService.ACTION_DISCARD, root);
        show(TAB_DRIVE);
    }
}
