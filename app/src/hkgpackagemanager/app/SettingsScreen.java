package hkgpackagemanager.app;

import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import hkgpackagemanager.core.PressCounter;
import hkgpackagemanager.core.ScreenText;
import hkgpackagemanager.core.Settings;
import hkgpackagemanager.core.StickLayout;
import java.util.List;

/** Which car button opens the app, how many presses, and the log copy. */
final class SettingsScreen implements Ui.Screen {
    private static final String[] BUTTON_LABELS = {"Off", "Navigation", "Media"};
    private static final hkgpackagemanager.core.Button[] BUTTONS = {
        hkgpackagemanager.core.Button.OFF, hkgpackagemanager.core.Button.NAVIGATION,
        hkgpackagemanager.core.Button.MEDIA};

    private final MainActivity act;
    private final NemesisCli cli;
    private final StateStore store;
    private final LinearLayout content;
    private final View view;
    private final Button[] buttonChoices = new Button[BUTTONS.length];
    private final Button[] pressChoices = new Button[4]; // 2..5
    private final TextView conflict;
    private final TextView logResult;
    private Settings settings = Settings.DEFAULT;

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
        content.addView(Ui.bold(a, "Presses needed"));
        LinearLayout presses = Ui.row(a);
        for (int i = 0; i < pressChoices.length; i++) {
            final int n = i + 2;
            pressChoices[i] = Ui.button(a, String.valueOf(n), new View.OnClickListener() {
                public void onClick(View v) { change(new Settings(settings.button, n)); }
            });
            presses.addView(pressChoices[i]);
        }
        content.addView(presses);
        conflict = Ui.text(a, ScreenText.CONFLICT_LINE);
        content.addView(conflict);
        content.addView(Ui.button(a, "Copy log to USB drive", new View.OnClickListener() {
            public void onClick(View v) { copyLog(); }
        }));
        logResult = Ui.text(a, "");
        content.addView(logResult);
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
}
