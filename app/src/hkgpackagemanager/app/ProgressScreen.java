package hkgpackagemanager.app;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import hkgpackagemanager.core.Queue;
import hkgpackagemanager.core.QueueItem;
import hkgpackagemanager.core.QueueRunner;
import hkgpackagemanager.core.ScreenText;

/** The multi-install queue as the service last reported it. */
final class ProgressScreen implements Ui.Screen {
    private final Context ctx;
    private final LinearLayout content;
    private final View view;

    ProgressScreen(Context c) {
        ctx = c;
        content = Ui.column(c);
        view = Ui.scroll(c, content);
    }

    @Override public View view() { return view; }

    @Override public void refresh() {
        content.removeAllViews();
        boolean running = QueueService.isRunning();
        Queue q = QueueService.current();
        QueueRunner.Outcome o = QueueService.lastOutcome();
        // the handler note only while a run swaps descriptors; afterwards, the R20 note if one was put back
        String note = ScreenText.descriptorNote(running, o != null && o.restoredDescriptor);
        if (note != null) content.addView(Ui.text(ctx, note));
        if (running) content.addView(Ui.bold(ctx, ScreenText.KEEP_DRIVE));
        if (q == null && o != null) q = o.queue;
        if (q == null) {
            content.addView(Ui.text(ctx, running ? "Starting…" : "No install has run yet."));
            return;
        }
        boolean over = !running;
        for (QueueItem i : q.items) {
            // once finished the failures are listed below with their reasons
            QueueItem shown = over && q.finished ? i.withState(i.state, "") : i;
            content.addView(Ui.text(ctx, ScreenText.itemLine(shown, i.id)));
        }
        if (over && q.finished) {
            content.addView(Ui.bold(ctx, ScreenText.finishedLine(q)));
            for (QueueItem i : q.items) {
                if (i.state == hkgpackagemanager.core.ItemState.FAILED) {
                    content.addView(Ui.text(ctx, ScreenText.itemLine(i, i.id)));
                }
            }
        } else if (over && o != null && o.stopped) {
            content.addView(Ui.bold(ctx, ScreenText.stoppedLine(o.stopReason)));
        }
    }
}
