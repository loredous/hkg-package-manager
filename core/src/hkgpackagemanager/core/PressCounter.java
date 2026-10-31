package hkgpackagemanager.core;

/** Counts button presses: {@code needed} presses, each within 1500 ms of the last, trigger once. */
public final class PressCounter {
    static final long WINDOW_MS = 1500;
    static final long REPEAT_MS = 200;

    private final int needed;
    private int count;
    private boolean hasLast;
    private long last;

    public PressCounter(int needed) {
        this.needed = needed;
    }

    /** True when this press completes the required number of presses. */
    public boolean press(long nowMs) {
        if (hasLast) {
            long gap = nowMs - last;
            if (gap < REPEAT_MS) return false;
            if (count > 0 && gap > WINDOW_MS) count = 0;
        }
        count++;
        hasLast = true;
        last = nowMs;
        if (count >= needed) {
            count = 0;
            return true;
        }
        return false;
    }

    /** NEMESIS Manager itself uses Navigation or Setup. */
    public static boolean conflictsWithNemesisManager(Button b) {
        return b == Button.NAVIGATION;
    }
}
