package hkgpackagemanager.core;

/** Which button opens the app and how many presses it takes. */
public final class Settings {
    public static final Settings DEFAULT = new Settings(Button.MEDIA, 3);

    public final Button button;
    public final int presses;

    public Settings(Button button, int presses) {
        this.button = button;
        this.presses = presses;
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
