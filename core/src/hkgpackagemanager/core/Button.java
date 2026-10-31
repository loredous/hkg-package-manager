package hkgpackagemanager.core;

import java.util.ArrayList;
import java.util.List;

/**
 * A car button that can open the app, with the broadcast action(s) the framework sends for it.
 * Values from the owner's key capture: docs/notes/key-capture.md.
 */
public enum Button {
    OFF(false),
    /** Pressed: one broadcast per short press. */
    NAVIGATION(false, "com.hkmc.intent.action.NAVI_SHORTCUT"),
    /** Held: the broadcast fires once, about 0.8 s into the hold. A short Media press sends no fixed action. */
    MEDIA_HOLD(true, "com.mobis.intent.action.MEDIAGATE_SHORTCUT");

    /** True when one broadcast is a whole hold, so a single one opens the app. */
    public final boolean hold;
    public final String[] actions;

    Button(boolean hold, String... actions) {
        this.hold = hold;
        this.actions = actions;
    }

    /** The first non-OFF button whose actions contain {@code action}, or null. */
    public static Button forAction(String action) {
        if (action == null) return null;
        for (Button b : values()) {
            if (b == OFF) continue;
            for (String a : b.actions) {
                if (a.equals(action)) return b;
            }
        }
        return null;
    }

    /** Every action of every button, de-duplicated, in enum order. */
    public static String[] allActions() {
        List<String> out = new ArrayList<String>();
        for (Button b : values()) {
            for (String a : b.actions) {
                if (!out.contains(a)) out.add(a);
            }
        }
        return out.toArray(new String[out.size()]);
    }
}
