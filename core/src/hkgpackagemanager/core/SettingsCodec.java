package hkgpackagemanager.core;

/** Text form of {@link Settings}: {@code button=media\npresses=3\n}. Decoding never throws. */
public final class SettingsCodec {
    private static final int MIN_PRESSES = 2;
    private static final int MAX_PRESSES = 5;

    private SettingsCodec() {}

    public static String encode(Settings s) {
        return "button=" + s.button.name().toLowerCase(java.util.Locale.ENGLISH)
            + "\npresses=" + s.presses + "\n";
    }

    public static Settings decode(String text) {
        Button button = Settings.DEFAULT.button;
        int presses = Settings.DEFAULT.presses;
        if (text != null) {
            for (String line : text.split("\n")) {
                int eq = line.indexOf('=');
                if (eq < 0) continue;
                String key = line.substring(0, eq).trim();
                String value = line.substring(eq + 1).trim();
                if (key.equals("button")) {
                    for (Button b : Button.values()) {
                        if (b.name().toLowerCase(java.util.Locale.ENGLISH).equals(value)) button = b;
                    }
                } else if (key.equals("presses")) {
                    try {
                        presses = Math.max(MIN_PRESSES, Math.min(MAX_PRESSES, Integer.parseInt(value)));
                    } catch (NumberFormatException e) {
                        presses = Settings.DEFAULT.presses;
                    }
                }
            }
        }
        return new Settings(button, presses);
    }
}
