package hkgpackagemanager.core;

import java.util.List;
import java.util.Map;

/** The wording and small decisions the screens share, kept out of the Android classes so they can be tested. */
public final class ScreenText {
    public static final String HANDLER_NOTE = "If NEMESIS asks to run an item from the USB drive, either answer is fine.";
    public static final String KEEP_DRIVE = "Don't remove the USB drive until the list finishes.";
    public static final String RESUME_PROMPT = "Unfinished install found: continue?";
    public static final String RESTART_LINE = "Restart the head unit to finish.";
    public static final String CONFLICT_LINE = "NEMESIS Manager may also open on this button.";
    /** After a run that put the stick's own NEMESIS.data back (Ruling R20): NEMESIS's handler acts on it at once. */
    public static final String RESTORED_NOTE = "NEMESIS may now ask about the drive's own NEMESIS.data. "
            + "Answer as you would when inserting the drive.";
    /** Added to the single Install/Update confirmation when the package is this manager. */
    public static final String SELF_UPDATE_WARNING = "The manager will close while it updates; open it again afterwards.";
    /** Discard asked for while the service runs a queue. */
    public static final String RUNNING_INFO = "An install is running.";

    /** Yellow verdict line on the confirmation. */
    public static final String YELLOW_WARNING = "This package hasn't been checked for this car.";
    /** Red verdict line on the confirmation. */
    public static final String RED_WARNING = "This package is marked as not working on this car.";
    /** The tick a red package needs before it can be installed. */
    public static final String RED_TICK = "I understand; install it anyway";
    /** Header for the loose zips that are not from a catalog. */
    public static final String LOOSE_HEADER = "Not from a catalog";
    /** Button on an untrusted catalog's row. */
    public static final String TRUST_BUTTON = "Trust this key";
    public static final String KEYS_NOT_SAVED = "Couldn't save the trusted keys.";

    private ScreenText() {}

    /** `New`, `Update (installed X, drive Y)`, `Same version`, or the zip's problem. */
    public static String statusText(StickZip zip, Map<String, InstalledPackage> installed) {
        switch (StatusLabeler.label(zip, installed)) {
            case NEW: return "New";
            case SAME: return "Same version";
            case UPDATE: {
                InstalledPackage p = installed.get(zip.meta.id);
                String have = p == null || p.version == null || p.version.length() == 0 ? "unknown" : p.version.trim();
                String drive = zip.meta.version.trim().length() == 0 ? "unknown" : zip.meta.version.trim();
                return "Update (installed " + have + ", drive " + drive + ")";
            }
            default: return zip.problem;
        }
    }

    /**
     * The note at the top of the Progress screen: the handler note while a queue runs, the
     * restored-descriptor note after a run that put the drive's own NEMESIS.data back, else none.
     */
    public static String descriptorNote(boolean running, boolean restoredDescriptor) {
        if (running) return HANDLER_NOTE;
        return restoredDescriptor ? RESTORED_NOTE : null;
    }

    /** `Update Name 1.2?` for one zip, with the self-update warning for this manager, then the verdict's warning. */
    public static String singleConfirm(String action, PackageMeta meta, Verdict v) {
        String q = action + " " + meta.name + (meta.version.length() == 0 ? "" : " " + meta.version) + "?";
        if (QueueOrder.SELF_ID.equals(meta.id)) q += "\n\n" + SELF_UPDATE_WARNING;
        if (v.colour == Colour.YELLOW) q += "\n\n" + YELLOW_WARNING + " (" + v.reason + ")";
        if (v.colour == Colour.RED) q += "\n\n" + RED_WARNING + " (" + v.reason + ")";
        return q;
    }

    /** The confirmation for the whole queue: the lines, the unchecked names, and the red-tick instruction. */
    public static String queueConfirm(List<String> lines, List<String> yellowNames, int redCount) {
        StringBuilder b = new StringBuilder("Install these packages?\n");
        for (String l : lines) b.append("\n").append(l);
        if (!yellowNames.isEmpty()) {
            b.append("\n\nNot checked for this car: ");
            for (int i = 0; i < yellowNames.size(); i++) b.append(i == 0 ? "" : ", ").append(yellowNames.get(i));
        }
        if (redCount > 0) b.append("\n\nTick each package marked as not working on this car to continue.");
        return b.toString();
    }

    /** The reason text shown beside a package's colour. */
    public static String verdictLine(Verdict v) {
        return v.reason;
    }

    /** A key fingerprint in groups of four characters. */
    public static String fingerprint(String hex) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < hex.length(); i += 4) {
            if (i > 0) b.append(' ');
            b.append(hex.substring(i, Math.min(hex.length(), i + 4)));
        }
        return b.toString();
    }

    /** The line shown for a catalog whose key is not trusted yet. */
    public static String untrustedLine(String name, String hex) {
        return "Untrusted catalog " + name + ", key fingerprint " + fingerprint(hex);
    }

    /** The confirmation before trusting a catalog's key. */
    public static String trustConfirm(String name, String hex) {
        return "Trust catalogs signed with this key?\n\n" + name + "\n" + fingerprint(hex)
                + "\n\nCheck the fingerprint with the catalog's publisher first.";
    }

    /** The button label for a row: Update for UPDATE, Install otherwise. */
    public static String actionLabel(ZipStatus s) {
        return s == ZipStatus.UPDATE ? "Update" : "Install";
    }

    /** `Finished: 3 installed, 1 failed`. */
    public static String finishedLine(Queue q) {
        return "Finished: " + q.doneCount() + " installed, " + q.failedCount() + " failed";
    }

    /** `Stopped: <reason>. Plug the drive back in to continue.` */
    public static String stoppedLine(String reason) {
        return "Stopped: " + reason + ". Plug the drive back in to continue.";
    }

    /** `name  (state)  message` for one queue item, without trailing blanks. */
    public static String itemLine(QueueItem i, String name) {
        String s = name + ": " + i.state.name().toLowerCase(java.util.Locale.US);
        return i.message.length() == 0 ? s : s + " - " + i.message;
    }

    /** The preferred root if it is mounted, else the first mounted one, else null. */
    public static String chooseStick(List<String> mounted, String preferred) {
        if (mounted.isEmpty()) return null;
        if (preferred != null && mounted.contains(preferred)) return preferred;
        return mounted.get(0);
    }

    /** The mounted roots with the preferred one (if mounted) moved to the front. */
    public static java.util.List<String> preferredFirst(List<String> mounted, String preferred) {
        java.util.List<String> out = new java.util.ArrayList<String>(mounted);
        if (preferred != null && out.remove(preferred)) out.add(0, preferred);
        return out;
    }

    public static String otherDriveMessage(String otherRoot) {
        return "Remove the other USB drive (" + otherRoot + ") first: NEMESIS only reads one drive.";
    }

    public static String uninstallResult(String output) {
        String o = output == null ? "" : output.trim();
        return RestartHint.mentionsRestart(o) ? (o.length() == 0 ? "" : o + "\n") + RESTART_LINE : o;
    }
}
