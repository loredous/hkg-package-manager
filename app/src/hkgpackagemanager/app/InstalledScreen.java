package hkgpackagemanager.app;

import android.view.View;
import android.widget.LinearLayout;
import hkgpackagemanager.core.CliResult;
import hkgpackagemanager.core.InstallStep;
import hkgpackagemanager.core.PkgList;
import hkgpackagemanager.core.PkgListEntry;
import hkgpackagemanager.core.ScreenText;
import java.util.ArrayList;
import java.util.List;

/** What NEMESIS has installed, with uninstall. */
final class InstalledScreen implements Ui.Screen {
    private static final String SELF = "hkg.package.manager";

    private final MainActivity act;
    private final NemesisCli cli;
    private final LinearLayout content;
    private final View view;
    private boolean busy;

    InstalledScreen(MainActivity a, NemesisCli cli) {
        this.act = a;
        this.cli = cli;
        content = Ui.column(a);
        view = Ui.scroll(a, content);
    }

    @Override public View view() { return view; }

    private static final class Data {
        String error;
        final List<PkgListEntry> entries = new ArrayList<PkgListEntry>();
        final List<String> versions = new ArrayList<String>();
    }

    @Override public void refresh() {
        content.removeAllViews();
        content.addView(Ui.text(act, "Loading…"));
        Ui.background(act, new Ui.Work<Data>() {
            public Data run() {
                Data d = new Data();
                CliResult r = cli.pkgList();
                if (r.exit != 0) {
                    String last = r.lastLine();
                    d.error = "Couldn't read the package list: " + (last == null ? "exit " + r.exit : last);
                    return d;
                }
                InstallStep step = new InstallStep(cli, "");
                for (PkgListEntry e : PkgList.parse(r.output)) {
                    d.entries.add(e);
                    d.versions.add(step.installedVersion(e.id));
                }
                return d;
            }
        }, new Ui.Done<Data>() {
            public void done(Data d) { render(d); }
        });
    }

    private void render(Data d) {
        content.removeAllViews();
        if (d == null) {
            content.addView(Ui.text(act, "Couldn't read the package list."));
            return;
        }
        if (d.error != null) {
            content.addView(Ui.text(act, d.error));
            return;
        }
        for (int i = 0; i < d.entries.size(); i++) {
            final PkgListEntry e = d.entries.get(i);
            String v = d.versions.get(i);
            LinearLayout row = Ui.row(act);
            LinearLayout info = Ui.column(act);
            info.addView(Ui.bold(act, e.name));
            info.addView(Ui.text(act, e.id + "   version " + (v == null || v.length() == 0 ? "unknown" : v)));
            info.addView(Ui.text(act, e.types + (e.locked ? "   locked" : "")));
            row.addView(info, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            if (!e.locked && !SELF.equals(e.id)) {
                row.addView(Ui.button(act, "Uninstall", new View.OnClickListener() {
                    public void onClick(View b) { confirmUninstall(e); }
                }));
            }
            content.addView(row);
        }
        if (d.entries.isEmpty()) content.addView(Ui.text(act, "No packages installed."));
    }

    private void confirmUninstall(final PkgListEntry e) {
        if (busy || QueueService.isRunning()) {
            Ui.info(act, null, "Wait for the running install to finish first.");
            return;
        }
        Ui.confirm(act, null, "Uninstall " + e.name + " (" + e.id + ")?", "Uninstall", new Runnable() {
            public void run() { uninstall(e); }
        }, "Cancel", null);
    }

    private void uninstall(final PkgListEntry e) {
        busy = true;
        final android.app.AlertDialog wait = Ui.busy(act, "Uninstalling " + e.name + "…");
        Ui.background(act, new Ui.Work<String>() {
            public String run() {
                CliResult r = cli.uninstall(e.id);
                String text = ScreenText.uninstallResult(r.output);
                if (text.length() == 0) text = r.exit == 0 ? "Uninstalled" : "uninstall failed: exit " + r.exit;
                return text;
            }
        }, new Ui.Done<String>() {
            public void done(String text) {
                busy = false;
                Ui.dismiss(wait);
                Ui.info(act, null, text == null ? "Uninstall failed." : text);
                refresh();
            }
        });
    }
}
