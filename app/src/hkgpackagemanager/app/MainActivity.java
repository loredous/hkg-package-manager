package hkgpackagemanager.app;

import android.app.Activity;

/** Stub until the screens arrive; it only carries the extras the service and receivers use. */
public class MainActivity extends Activity {
    /** boolean: an unfinished queue exists, ask whether to continue it. */
    public static final String EXTRA_RESUME = "resume";
    /** String: the stick root the unfinished queue is on. */
    public static final String EXTRA_ROOT = "root";
    /** boolean: show the Progress screen (the service just finished or stopped a run). */
    public static final String EXTRA_PROGRESS = "progress";
}
