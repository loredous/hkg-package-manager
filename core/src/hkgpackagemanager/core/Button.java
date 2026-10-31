package hkgpackagemanager.core;

import java.util.ArrayList;
import java.util.List;

/** A car button that can open the app, with the broadcast action(s) the framework sends for it. */
public enum Button {
    OFF(),
    NAVIGATION("com.hkmc.intent.action.NAVI_SHORTCUT"),
    /**
     * Provisional until docs/notes/key-capture.md is filled from a kit run; trim to the confirmed action(s).
     */
    MEDIA(
        "com.mobis.intent.action.MEDIAGATE_SHORTCUT",
        "com.hkmc.intent.action.MEDIA_BLUETOOTH_SHORTCUT",
        "com.hkmc.intent.action.MEDIA_MUSIC_SHORTCUT",
        "com.hkmc.intent.action.MEDIA_USB_AUDIO_SHORTCUT",
        "com.hkmc.intent.action.MEDIA_USB_VIDEO_SHORTCUT",
        "com.hkmc.intent.action.MEDIA_AUDIOCD_SHORTCUT",
        "com.hkmc.intent.action.MEDIA_MCONNECT_SHORTCUT");

    public final String[] actions;

    Button(String... actions) {
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
