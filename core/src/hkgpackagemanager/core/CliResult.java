package hkgpackagemanager.core;

/** Exit code and combined output of one NEMESIS CLI call. */
public final class CliResult {
    public final int exit;
    public final String output;

    public CliResult(int exit, String output) {
        this.exit = exit;
        this.output = output == null ? "" : output;
    }

    /** The last non-empty trimmed line of the output, or null. */
    public String lastLine() {
        String[] lines = output.split("\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            String t = lines[i].trim();
            if (t.length() > 0) return t;
        }
        return null;
    }
}
