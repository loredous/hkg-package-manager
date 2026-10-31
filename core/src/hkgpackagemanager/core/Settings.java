package hkgpackagemanager.core;

/** Which button opens the app and how many presses it takes (presses apply only to a pressed button). */
public final class Settings {
    public static final Settings DEFAULT = new Settings(Button.MEDIA_HOLD, 3);

    public final Button button;
    public final int presses;

    public Settings(Button button, int presses) {
        this.button = button;
        this.presses = presses;
    }

    /** Broadcasts needed to open the app: one for a held button, else {@link #presses}. */
    public int broadcastsNeeded() {
        return button.hold ? 1 : presses;
    }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Settings)) return false;
        Settings s = (Settings) o;
        return button == s.button && presses == s.presses;
    }

    @Override public int hashCode() {
        return button.hashCode() * 31 + presses;
    }

    @Override public String toString() {
        return "Settings(" + button + ", " + presses + ")";
    }
}
