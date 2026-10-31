package hkgpackagemanager.app;

import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import hkgpackagemanager.core.BadCatalogException;
import hkgpackagemanager.core.CatalogKey;
import hkgpackagemanager.core.PressCounter;
import hkgpackagemanager.core.ScreenText;
import hkgpackagemanager.core.Settings;
import hkgpackagemanager.core.StickLayout;
import hkgpackagemanager.core.TrustedKeys;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Which car button opens the app, how many presses (for a pressed button), the log copy, and the trusted catalog keys. */
final class SettingsScreen implements Ui.Screen {
    private static final String[] BUTTON_LABELS = {"Off", "Press Navigation", "Hold Media"};
    private static final String KEYS_DIR = "HKGPM/keys";
    private static final int MAX_KEY_BYTES = 16 * 1024;
    private static final hkgpackagemanager.core.Button[] BUTTONS = {
        hkgpackagemanager.core.Button.OFF, hkgpackagemanager.core.Button.NAVIGATION,
        hkgpackagemanager.core.Button.MEDIA_HOLD};

    private final MainActivity act;
    private final NemesisCli cli;
    private final StateStore store;
    private final LinearLayout content;
    private final View view;
    private final Button[] buttonChoices = new Button[BUTTONS.length];
    private final Button[] pressChoices = new Button[4]; // 2..5
    private final TextView pressesLabel;
    private final LinearLayout pressesRow;
    private final TextView conflict;
    private final TextView logResult;
    private final LinearLayout keysBox;
    private final TextView keysResult;
    private Settings settings = Settings.DEFAULT;
    private TrustedKeys keys = TrustedKeys.NONE;

    /** A key file that parsed and isn't trusted yet. */
    private static final class Found {
        final String fileName;
        final String fingerprint;

        Found(String fileName, String fingerprint) {
            this.fileName = fileName;
            this.fingerprint = fingerprint;
        }
    }

    /** What reading the keys folder found. */
    private static final class Scan {
        boolean noStick;
        final List<Found> found = new ArrayList<Found>();
        final List<String> problems = new ArrayList<String>();
    }

    SettingsScreen(MainActivity a, NemesisCli cli) {
        this.act = a;
        this.cli = cli;
        this.store = new StateStore(a, cli);
        content = Ui.column(a);
        view = Ui.scroll(a, content);
        content.addView(Ui.bold(a, "Open this app with the car button"));
        LinearLayout buttons = Ui.row(a);
        for (int i = 0; i < BUTTONS.length; i++) {
            final hkgpackagemanager.core.Button b = BUTTONS[i];
            buttonChoices[i] = Ui.button(a, BUTTON_LABELS[i], new View.OnClickListener() {
                public void onClick(View v) { change(new Settings(b, settings.presses)); }
            });
            buttons.addView(buttonChoices[i]);
        }
        content.addView(buttons);
        pressesLabel = Ui.bold(a, "Presses needed");
        content.addView(pressesLabel);
        pressesRow = Ui.row(a);
        for (int i = 0; i < pressChoices.length; i++) {
            final int n = i + 2;
            pressChoices[i] = Ui.button(a, String.valueOf(n), new View.OnClickListener() {
                public void onClick(View v) { change(new Settings(settings.button, n)); }
            });
            pressesRow.addView(pressChoices[i]);
        }
        content.addView(pressesRow);
        conflict = Ui.text(a, ScreenText.CONFLICT_LINE);
        content.addView(conflict);
        content.addView(Ui.button(a, "Copy log to USB drive", new View.OnClickListener() {
            public void onClick(View v) { copyLog(); }
        }));
        logResult = Ui.text(a, "");
        content.addView(logResult);
        content.addView(Ui.bold(a, "Catalog keys"));
        keysBox = Ui.column(a);
        content.addView(keysBox);
        content.addView(Ui.button(a, "Add keys from the USB drive", new View.OnClickListener() {
            public void onClick(View v) { addKeys(); }
        }));
        keysResult = Ui.text(a, "");
        content.addView(keysResult);
        show();
    }

    @Override public View view() { return view; }

    @Override public void refresh() {
        Ui.background(act, new Ui.Work<Settings>() {
            public Settings run() { return store.load(); }
        }, new Ui.Done<Settings>() {
            public void done(Settings s) {
                if (s != null) settings = s;
                show();
            }
        });
        Ui.background(act, new Ui.Work<TrustedKeys>() {
            public TrustedKeys run() { return store.loadKeys(); }
        }, new Ui.Done<TrustedKeys>() {
            public void done(TrustedKeys k) {
                if (k != null) keys = k;
                showKeys();
            }
        });
    }

    private void change(final Settings s) {
        settings = s;
        show();
        Ui.background(act, new Ui.Work<Boolean>() {
            public Boolean run() {
                store.save(s);
                return Boolean.TRUE;
            }
        }, new Ui.Done<Boolean>() {
            public void done(Boolean ok) { }
        });
    }

    private void show() {
        for (int i = 0; i < BUTTONS.length; i++) buttonChoices[i].setEnabled(BUTTONS[i] != settings.button);
        for (int i = 0; i < pressChoices.length; i++) pressChoices[i].setEnabled(i + 2 != settings.presses);
        // a held button opens on one hold; Off has nothing to count
        int pressVisibility = settings.button.hold || settings.button == hkgpackagemanager.core.Button.OFF
            ? View.GONE : View.VISIBLE;
        pressesLabel.setVisibility(pressVisibility);
        pressesRow.setVisibility(pressVisibility);
        conflict.setVisibility(PressCounter.conflictsWithNemesisManager(settings.button) ? View.VISIBLE : View.GONE);
    }

    private void copyLog() {
        logResult.setText("Copying…");
        Ui.background(act, new Ui.Work<String>() {
            public String run() throws Exception {
                List<String> mounted = Sticks.mounted();
                String root = ScreenText.chooseStick(mounted, null);
                if (root == null) return "No USB drive found.";
                new StickFs(act, root, cli).writeAtomic(StickLayout.LOG, AppLog.get(act).snapshot());
                return "Log copied to " + root + "/" + StickLayout.LOG;
            }
        }, new Ui.Done<String>() {
            public void done(String msg) {
                logResult.setText(msg == null ? "Couldn't copy the log." : msg);
            }
        });
    }

    private void showKeys() {
        keysBox.removeAllViews();
        if (keys.labels().isEmpty()) {
            keysBox.addView(Ui.text(act, "No trusted keys."));
            return;
        }
        for (Map.Entry<String, String> e : keys.labels().entrySet()) {
            final String fp = e.getKey();
            final String label = e.getValue().length() == 0
                ? ScreenText.fingerprint(fp.substring(0, Math.min(16, fp.length()))) : e.getValue();
            LinearLayout row = Ui.row(act);
            LinearLayout names = Ui.column(act);
            names.addView(Ui.text(act, label));
            TextView print = Ui.text(act, ScreenText.fingerprint(fp));
            print.setTextColor(Ui.GREY);
            names.addView(print);
            row.addView(names, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(Ui.button(act, "Remove", new View.OnClickListener() {
                public void onClick(View v) { confirmRemove(fp, label); }
            }));
            keysBox.addView(row);
        }
    }

    private void confirmRemove(final String fp, String label) {
        Ui.confirm(act, null, "Stop trusting " + label + "?", "Remove", new Runnable() {
            public void run() {
                keys = keys.without(fp);
                showKeys();
                saveKeys(keys, null);
            }
        }, "Cancel", null);
    }

    /** Saves on a background thread, then runs {@code then} (may be null) on the UI thread. */
    private void saveKeys(final TrustedKeys k, final Runnable then) {
        Ui.background(act, new Ui.Work<Boolean>() {
            public Boolean run() {
                store.saveKeys(k);
                return Boolean.TRUE;
            }
        }, new Ui.Done<Boolean>() {
            public void done(Boolean ok) { if (then != null) then.run(); }
        });
    }

    private void addKeys() {
        keysResult.setText("Reading the drive…");
        final TrustedKeys trusted = keys;
        Ui.background(act, new Ui.Work<Scan>() {
            public Scan run() {
                Scan scan = new Scan();
                String root = ScreenText.chooseStick(Sticks.mounted(), null);
                if (root == null) {
                    scan.noStick = true;
                    return scan;
                }
                File[] files = new File(root, KEYS_DIR).listFiles();
                if (files == null) return scan;
                Arrays.sort(files);
                for (File f : files) {
                    String name = f.getName();
                    if (!name.toLowerCase().endsWith(".pem") || !f.isFile()) continue;
                    try {
                        CatalogKey key = CatalogKey.fromPem(readCapped(f));
                        if (!trusted.trusts(key.fingerprint)) scan.found.add(new Found(name, key.fingerprint));
                    } catch (BadCatalogException e) {
                        scan.problems.add(name + ": " + e.getMessage());
                    } catch (IOException e) {
                        scan.problems.add(name + ": can't read the file");
                    }
                }
                return scan;
            }
        }, new Ui.Done<Scan>() {
            public void done(Scan scan) {
                if (scan == null) {
                    keysResult.setText("Couldn't read the drive.");
                } else if (scan.noStick) {
                    keysResult.setText("No USB drive found.");
                } else {
                    askNext(scan, 0, 0);
                }
            }
        });
    }

    /** The file's text, or a BadCatalogException when it is over the size cap. */
    private static String readCapped(File f) throws IOException, BadCatalogException {
        InputStream in = new FileInputStream(f);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[2048];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                if (out.size() > MAX_KEY_BYTES) throw new BadCatalogException("file too large for a key");
            }
            return new String(out.toByteArray(), "UTF-8");
        } finally {
            in.close();
        }
    }

    /** One trust dialog at a time; the next opens when this one closes, yes or no. */
    private void askNext(final Scan scan, final int index, final int added) {
        if (index >= scan.found.size()) {
            StringBuilder sb = new StringBuilder();
            for (String p : scan.problems) sb.append(p).append('\n');
            if (scan.found.isEmpty()) sb.append("No new keys in HKGPM/keys on the drive.");
            else sb.append("Added ").append(added).append(" key(s).");
            keysResult.setText(sb.toString());
            return;
        }
        final Found f = scan.found.get(index);
        final Runnable skip = new Runnable() {
            public void run() { askNext(scan, index + 1, added); }
        };
        Ui.confirm(act, null, ScreenText.trustConfirm(f.fileName, f.fingerprint), ScreenText.TRUST_BUTTON,
                new Runnable() {
                    public void run() {
                        keys = keys.with(f.fingerprint, f.fileName);
                        showKeys();
                        saveKeys(keys, new Runnable() {
                            public void run() { askNext(scan, index + 1, added + 1); }
                        });
                    }
                }, "Cancel", skip, skip);
    }
}
