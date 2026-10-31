package hkgpackagemanager.app;

import android.app.AlertDialog;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import hkgpackagemanager.core.CliResult;
import hkgpackagemanager.core.InstallStep;
import hkgpackagemanager.core.InstalledPackage;
import hkgpackagemanager.core.ItemState;
import hkgpackagemanager.core.PkgList;
import hkgpackagemanager.core.PkgListEntry;
import hkgpackagemanager.core.QueueItem;
import hkgpackagemanager.core.QueuePreflight;
import hkgpackagemanager.core.ScreenText;
import hkgpackagemanager.core.StatusLabeler;
import hkgpackagemanager.core.StickLayout;
import hkgpackagemanager.core.StickScanner;
import hkgpackagemanager.core.StickZip;
import hkgpackagemanager.core.ZipStatus;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The zips on the stick, their status, and single or multiple install. */
final class DriveScreen implements Ui.Screen {
    private final MainActivity act;
    private final NemesisCli cli;
    private final LinearLayout content;
    private final View view;
    private final Set<String> checked = new HashSet<String>(); // relPaths
    private String stick;
    private List<StickZip> zips = new ArrayList<StickZip>();
    private Map<String, InstalledPackage> installed = new HashMap<String, InstalledPackage>();
    private List<String> mounted = new ArrayList<String>();
    private String error;
    private boolean busy;

    DriveScreen(MainActivity a, NemesisCli cli) {
        this.act = a;
        this.cli = cli;
        content = Ui.column(a);
        view = Ui.scroll(a, content);
    }

    @Override public View view() { return view; }

    private static final class Data {
        List<String> mounted = new ArrayList<String>();
        String stick;
        List<StickZip> zips = new ArrayList<StickZip>();
        Map<String, InstalledPackage> installed = new HashMap<String, InstalledPackage>();
        String error;
    }

    @Override public void refresh() {
        content.removeAllViews();
        content.addView(Ui.text(act, "Reading the drive…"));
        final String want = stick;
        Ui.background(act, new Ui.Work<Data>() {
            public Data run() {
                Data d = new Data();
                d.mounted = Sticks.mounted();
                d.stick = ScreenText.chooseStick(d.mounted, want);
                if (d.stick == null) return d;
                d.zips = StickScanner.scan(new File(d.stick));
                Set<String> onStick = new HashSet<String>();
                for (StickZip z : d.zips) if (z.meta != null) onStick.add(z.meta.id);
                CliResult r = cli.pkgList();
                if (r.exit != 0) {
                    String last = r.lastLine();
                    d.error = "Couldn't read the package list: " + (last == null ? "exit " + r.exit : last);
                    return d;
                }
                InstallStep step = new InstallStep(cli, d.stick);
                for (PkgListEntry e : PkgList.parse(r.output)) {
                    if (onStick.contains(e.id)) d.installed.put(e.id, new InstalledPackage(e, step.installedVersion(e.id)));
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
            content.addView(Ui.text(act, "Couldn't read the drive."));
            return;
        }
        stick = d.stick;
        mounted = d.mounted;
        error = d.error;
        zips = d.zips;
        installed = d.installed;
        if (stick == null) {
            content.addView(Ui.text(act, "No USB drive found."));
            return;
        }
        if (d.mounted.size() > 1) {
            LinearLayout pick = Ui.row(act);
            for (final String root : d.mounted) {
                Button b = Ui.button(act, root.substring(root.lastIndexOf('/') + 1), new View.OnClickListener() {
                    public void onClick(View v) {
                        stick = root;
                        checked.clear();
                        refresh();
                    }
                });
                b.setEnabled(!root.equals(stick));
                pick.addView(b);
            }
            content.addView(pick);
        }
        if (d.error != null) content.addView(Ui.text(act, d.error));
        LinearLayout bar = Ui.row(act);
        bar.addView(Ui.button(act, "Select all", new View.OnClickListener() {
            public void onClick(View v) {
                for (StickZip z : zips) if (z.meta != null) checked.add(z.relPath);
                render(snapshot());
            }
        }));
        bar.addView(Ui.button(act, "Install selected", new View.OnClickListener() {
            public void onClick(View v) { installSelected(); }
        }));
        content.addView(bar);
        if (zips.isEmpty()) content.addView(Ui.text(act, "No zips found on the drive."));
        for (StickZip z : zips) content.addView(row(z));
    }

    private Data snapshot() {
        Data d = new Data();
        d.mounted = mounted;
        d.error = error;
        d.stick = stick;
        d.zips = zips;
        d.installed = installed;
        return d;
    }

    private View row(final StickZip z) {
        LinearLayout row = Ui.row(act);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout info = Ui.column(act);
        final ZipStatus status = StatusLabeler.label(z, installed);
        if (z.meta == null) {
            info.addView(Ui.text(act, z.relPath));
            android.widget.TextView p = Ui.text(act, z.problem);
            p.setTextColor(Ui.GREY);
            info.addView(p);
            row.addView(info);
            return row;
        }
        CheckBox box = new CheckBox(act);
        box.setMinimumWidth(Ui.dp(act, Ui.TARGET_DP));
        box.setMinimumHeight(Ui.dp(act, Ui.TARGET_DP));
        box.setChecked(checked.contains(z.relPath));
        box.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) {
                if (on) checked.add(z.relPath); else checked.remove(z.relPath);
            }
        });
        row.addView(box);
        info.addView(Ui.bold(act, z.meta.name));
        info.addView(Ui.text(act, z.meta.id + "   " + z.meta.version));
        info.addView(Ui.text(act, ScreenText.statusText(z, installed)));
        row.addView(info, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        row.addView(Ui.button(act, ScreenText.actionLabel(status), new View.OnClickListener() {
            public void onClick(View v) { confirmSingle(z, status); }
        }));
        return row;
    }

    private boolean refuseWhileRunning() {
        if (busy || QueueService.isRunning()) {
            Ui.info(act, null, "Wait for the running install to finish first.");
            return true;
        }
        return false;
    }

    private void confirmSingle(final StickZip z, final ZipStatus status) {
        if (refuseWhileRunning()) return;
        final String root = stick;
        Ui.confirm(act, null, ScreenText.singleConfirm(ScreenText.actionLabel(status), z.meta),
                ScreenText.actionLabel(status), new Runnable() {
                    public void run() { single(root, z, status != ZipStatus.NEW); }
                }, "Cancel", null);
    }

    private void single(final String root, final StickZip z, final boolean update) {
        busy = true;
        final AlertDialog wait = Ui.busy(act, "Installing " + z.meta.name + "…");
        Ui.background(act, new Ui.Work<String>() {
            public String run() {
                QueueItem item = new QueueItem(z.relPath, 0, "", z.meta.id, z.meta.version, update,
                        ItemState.INSTALLING, "");
                QueueItem r = new InstallStep(cli, root).perform(item);
                return r.state == ItemState.DONE ? "Installed" : r.message;
            }
        }, new Ui.Done<String>() {
            public void done(String msg) {
                busy = false;
                Ui.dismiss(wait);
                Ui.info(act, null, msg == null ? "Install failed." : msg);
                refresh();
            }
        });
    }

    private void installSelected() {
        if (refuseWhileRunning()) return;
        final String root = stick;
        final List<StickZip> picked = new ArrayList<StickZip>();
        for (StickZip z : zips) if (z.meta != null && checked.contains(z.relPath)) picked.add(z);
        if (picked.isEmpty()) {
            Ui.info(act, null, "Select at least one package.");
            return;
        }
        Ui.background(act, new Ui.Work<String>() {
            public String run() {
                String other = QueuePreflight.otherDescriptorRoot(Sticks.mounted(), root, new QueuePreflight.Exists() {
                    public boolean at(String r) { return new File(r, StickLayout.DESCRIPTOR).exists(); }
                });
                return other == null ? "" : other;
            }
        }, new Ui.Done<String>() {
            public void done(String other) {
                if (other == null) other = "";
                if (other.length() > 0) {
                    Ui.info(act, null, ScreenText.otherDriveMessage(other));
                } else {
                    confirmQueue(root, picked);
                }
            }
        });
    }

    private void confirmQueue(final String root, final List<StickZip> picked) {
        StringBuilder sb = new StringBuilder("Install these packages?\n");
        final String[] paths = new String[picked.size()];
        final boolean[] updates = new boolean[picked.size()];
        for (int i = 0; i < paths.length; i++) {
            StickZip z = picked.get(i);
            paths[i] = z.relPath;
            updates[i] = StatusLabeler.label(z, installed) != ZipStatus.NEW;
            sb.append("\n").append(z.meta.name).append(" ").append(z.meta.version);
        }
        Ui.confirm(act, null, sb.toString(), "Install", new Runnable() {
            public void run() {
                android.content.Intent i = new android.content.Intent(act, QueueService.class)
                        .setAction(QueueService.ACTION_START)
                        .putExtra(QueueService.EXTRA_ROOT, root)
                        .putExtra(QueueService.EXTRA_PATHS, paths)
                        .putExtra(QueueService.EXTRA_UPDATES, updates);
                act.startService(i);
                checked.clear();
                act.show(MainActivity.TAB_PROGRESS);
            }
        }, "Cancel", null);
    }
}
