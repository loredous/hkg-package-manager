package hkgpackagemanager.app;

import android.app.AlertDialog;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import hkgpackagemanager.core.CarFacts;
import hkgpackagemanager.core.CatalogReader;
import hkgpackagemanager.core.CatalogView;
import hkgpackagemanager.core.CatalogZip;
import hkgpackagemanager.core.CliResult;
import hkgpackagemanager.core.Colour;
import hkgpackagemanager.core.CompatCheck;
import hkgpackagemanager.core.FileFingerprint;
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
import hkgpackagemanager.core.TrustedKeys;
import hkgpackagemanager.core.Verdict;
import hkgpackagemanager.core.ZipFingerprint;
import hkgpackagemanager.core.ZipStatus;
import hkgpackagemanager.core.ZipVerify;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The zips and catalogs on the stick, their status and colours, and single or multiple install. */
final class DriveScreen implements Ui.Screen {
    private final MainActivity act;
    private final NemesisCli cli;
    private final LinearLayout content;
    private final View view;
    private final Set<String> checked = new HashSet<String>(); // relPaths
    private final Map<String, Cached> fingerprints = new HashMap<String, Cached>();
    private String stick;
    private List<StickZip> zips = new ArrayList<StickZip>();
    private List<CatalogView> views = new ArrayList<CatalogView>();
    private TrustedKeys keys = TrustedKeys.NONE;
    private CarFacts facts = new CarFacts("", null);
    private Set<String> installedIds = new HashSet<String>();
    private Map<String, InstalledPackage> installed = new HashMap<String, InstalledPackage>();
    private List<String> mounted = new ArrayList<String>();
    private String error;
    private boolean busy;

    /** A fingerprint with the size and mtime it was taken at. */
    private static final class Cached {
        final long size;
        final long modified;
        final ZipFingerprint print;

        Cached(long size, long modified, ZipFingerprint print) {
            this.size = size;
            this.modified = modified;
            this.print = print;
        }
    }

    /** One package picked for the queue. */
    private static final class Pick {
        final String relPath;
        final String name;
        final String version;
        final boolean update;
        final Colour colour;

        Pick(String relPath, String name, String version, boolean update, Colour colour) {
            this.relPath = relPath;
            this.name = name;
            this.version = version;
            this.update = update;
            this.colour = colour;
        }
    }

    DriveScreen(MainActivity a, NemesisCli cli) {
        this.act = a;
        this.cli = cli;
        content = Ui.column(a);
        view = Ui.scroll(a, content);
    }

    @Override public View view() { return view; }

    /** Fingerprints a zip, reusing the last one while its size and mtime are unchanged. */
    private final CatalogReader.Fingerprinter caching = new CatalogReader.Fingerprinter() {
        @Override public ZipFingerprint of(File f) throws IOException {
            String key = f.getPath();
            long size = f.length();
            long modified = f.lastModified();
            synchronized (fingerprints) {
                Cached c = fingerprints.get(key);
                if (c != null && c.size == size && c.modified == modified) return c.print;
            }
            ZipFingerprint p = FileFingerprint.of(f);
            synchronized (fingerprints) {
                fingerprints.put(key, new Cached(size, modified, p));
            }
            return p;
        }
    };

    private static final class Data {
        List<String> mounted = new ArrayList<String>();
        String stick;
        List<StickZip> zips = new ArrayList<StickZip>();
        List<CatalogView> views = new ArrayList<CatalogView>();
        TrustedKeys keys = TrustedKeys.NONE;
        CarFacts facts = new CarFacts("", null);
        Set<String> installedIds = new HashSet<String>();
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
                d.keys = new StateStore(act, cli).loadKeys();
                d.views = CatalogReader.read(new File(d.stick), d.keys, caching);
                d.facts = CarProbe.facts(cli);
                Set<String> wanted = new HashSet<String>();
                for (StickZip z : d.zips) if (z.meta != null) wanted.add(z.meta.id);
                for (CatalogView v : d.views) {
                    for (CatalogZip z : v.zips) if (z.entry.problem == null) wanted.add(z.entry.id);
                }
                CliResult r = cli.pkgList();
                if (r.exit != 0) {
                    String last = r.lastLine();
                    d.error = "Couldn't read the package list: " + (last == null ? "exit " + r.exit : last);
                    return d;
                }
                InstallStep step = new InstallStep(cli, d.stick);
                for (PkgListEntry e : PkgList.parse(r.output)) {
                    d.installedIds.add(e.id);
                    if (wanted.contains(e.id)) d.installed.put(e.id, new InstalledPackage(e, step.installedVersion(e.id)));
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
        views = d.views;
        keys = d.keys;
        facts = d.facts;
        installedIds = d.installedIds;
        installed = d.installed;
        if (stick == null) {
            content.addView(Ui.text(act, "No USB drive found."));
            return;
        }
        if (d.mounted.size() > 1) content.addView(drivePicker(d.mounted));
        if (d.error != null) content.addView(Ui.text(act, d.error));
        content.addView(actionBar());
        for (CatalogView v : views) addCatalog(v);
        if (!zips.isEmpty() || views.isEmpty()) addLoose();
    }

    private Data snapshot() {
        Data d = new Data();
        d.mounted = mounted;
        d.error = error;
        d.stick = stick;
        d.zips = zips;
        d.views = views;
        d.keys = keys;
        d.facts = facts;
        d.installedIds = installedIds;
        d.installed = installed;
        return d;
    }

    private View drivePicker(List<String> roots) {
        LinearLayout pick = Ui.row(act);
        for (final String root : roots) {
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
        return pick;
    }

    private View actionBar() {
        LinearLayout bar = Ui.row(act);
        bar.addView(Ui.button(act, "Select all", new View.OnClickListener() {
            public void onClick(View v) {
                selectAll();
                render(snapshot());
            }
        }));
        bar.addView(Ui.button(act, "Install selected", new View.OnClickListener() {
            public void onClick(View v) { installSelected(); }
        }));
        return bar;
    }

    /** Ticks every installable row except red ones; each red row is ticked by hand. */
    private void selectAll() {
        for (StickZip z : zips) if (z.meta != null) checked.add(z.relPath);
        for (CatalogView v : views) {
            if (v.trust != CatalogView.Trust.TRUSTED) continue;
            for (CatalogZip z : v.zips) {
                if (z.installable() && verdictOf(z).colour != Colour.RED) checked.add(z.relPath);
            }
        }
    }

    /** The colour for a catalog entry, or null for an invalid entry (it has no usable compat data). */
    private Verdict verdictOf(CatalogZip z) {
        if (z.entry.problem != null) return null;
        return CompatCheck.check(z.entry.compat, facts, installedIds);
    }

    private static int colourOf(Verdict v) {
        return v.colour == Colour.RED ? Ui.RED : v.colour == Colour.YELLOW ? Ui.YELLOW : Ui.GREEN;
    }

    private TextView grey(String s) {
        TextView t = Ui.text(act, s);
        t.setTextColor(Ui.GREY);
        return t;
    }

    private TextView verdictText(Verdict v) {
        TextView t = Ui.text(act, ScreenText.verdictLine(v));
        t.setTextColor(colourOf(v));
        return t;
    }

    private void addCatalog(final CatalogView v) {
        if (v.trust == CatalogView.Trust.TRUSTED) {
            content.addView(Ui.bold(act, v.index.name));
            content.addView(grey(v.index.publisher));
            for (CatalogZip z : v.zips) content.addView(catalogRow(z));
        } else if (v.trust == CatalogView.Trust.UNTRUSTED) {
            content.addView(Ui.text(act, ScreenText.untrustedLine(v.index.name, v.fingerprint)));
            content.addView(Ui.button(act, ScreenText.TRUST_BUTTON, new View.OnClickListener() {
                public void onClick(View b) { confirmTrust(v); }
            }));
        } else {
            content.addView(Ui.bold(act, v.folder));
            content.addView(grey(v.error));
        }
    }

    private void confirmTrust(final CatalogView v) {
        Ui.confirm(act, null, ScreenText.trustConfirm(v.index.name, v.fingerprint), ScreenText.TRUST_BUTTON,
                new Runnable() {
                    public void run() {
                        Ui.background(act, new Ui.Work<Boolean>() {
                            public Boolean run() {
                                new StateStore(act, cli).addKey(v.fingerprint, v.index.name);
                                return Boolean.TRUE;
                            }
                        }, new Ui.Done<Boolean>() {
                            public void done(Boolean ok) { refresh(); }
                        });
                    }
                }, "Cancel", null);
    }

    private void addLoose() {
        content.addView(Ui.bold(act, ScreenText.LOOSE_HEADER));
        if (zips.isEmpty()) content.addView(Ui.text(act, "No zips found on the drive."));
        for (StickZip z : zips) content.addView(looseRow(z));
    }

    private CheckBox checkBox(final String relPath) {
        CheckBox box = new CheckBox(act);
        box.setMinimumWidth(Ui.dp(act, Ui.TARGET_DP));
        box.setMinimumHeight(Ui.dp(act, Ui.TARGET_DP));
        box.setChecked(checked.contains(relPath));
        box.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton b, boolean on) {
                if (on) checked.add(relPath); else checked.remove(relPath);
            }
        });
        return box;
    }

    private static String displayName(CatalogZip z) {
        if (z.entry.name != null) return z.entry.name;
        return z.entry.id != null ? z.entry.id : "(invalid entry)";
    }

    private View catalogRow(final CatalogZip z) {
        LinearLayout row = Ui.row(act);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout info = Ui.column(act);
        final Verdict verdict = verdictOf(z);
        info.addView(Ui.bold(act, displayName(z)));
        if (verdict != null) {
            info.addView(Ui.text(act, z.entry.id + "   " + z.entry.version));
            if (z.entry.description != null && z.entry.description.length() > 0) {
                info.addView(Ui.text(act, z.entry.description));
            }
            info.addView(verdictText(verdict));
        }
        if (!z.installable()) {
            info.addView(grey(z.problem));
            row.addView(info, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            return row;
        }
        row.addView(checkBox(z.relPath));
        final ZipStatus status = StatusLabeler.label(z.zip, installed);
        info.addView(Ui.text(act, ScreenText.statusText(z.zip, installed)));
        row.addView(info, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        row.addView(Ui.button(act, ScreenText.actionLabel(status), new View.OnClickListener() {
            public void onClick(View b) {
                QueueItem item = new QueueItem(z.relPath, z.entry.size, z.entry.sha256, z.zip.meta.id,
                        z.zip.meta.version, status != ZipStatus.NEW, ItemState.INSTALLING, "");
                confirmSingle(z.zip, status, verdict, item);
            }
        }));
        return row;
    }

    private View looseRow(final StickZip z) {
        LinearLayout row = Ui.row(act);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout info = Ui.column(act);
        if (z.meta == null) {
            info.addView(Ui.text(act, z.relPath));
            info.addView(grey(z.problem));
            row.addView(info);
            return row;
        }
        final ZipStatus status = StatusLabeler.label(z, installed);
        row.addView(checkBox(z.relPath));
        info.addView(Ui.bold(act, z.meta.name));
        info.addView(Ui.text(act, z.meta.id + "   " + z.meta.version));
        info.addView(verdictText(CompatCheck.LOOSE));
        info.addView(Ui.text(act, ScreenText.statusText(z, installed)));
        row.addView(info, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        row.addView(Ui.button(act, ScreenText.actionLabel(status), new View.OnClickListener() {
            public void onClick(View v) {
                QueueItem item = new QueueItem(z.relPath, 0, "", z.meta.id, z.meta.version,
                        status != ZipStatus.NEW, ItemState.INSTALLING, "");
                confirmSingle(z, status, CompatCheck.LOOSE, item);
            }
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

    private void confirmSingle(final StickZip z, final ZipStatus status, Verdict verdict, final QueueItem item) {
        if (refuseWhileRunning()) return;
        final String root = stick;
        String action = ScreenText.actionLabel(status);
        String message = ScreenText.singleConfirm(action, z.meta, verdict);
        Runnable go = new Runnable() {
            public void run() { single(root, z.meta.name, item); }
        };
        if (verdict.colour == Colour.RED) {
            Ui.confirmWithTick(act, message, ScreenText.RED_TICK, action, go);
        } else {
            Ui.confirm(act, null, message, action, go, "Cancel", null);
        }
    }

    private void single(final String root, final String name, final QueueItem item) {
        busy = true;
        final AlertDialog wait = Ui.busy(act, "Installing " + name + "…");
        Ui.background(act, new Ui.Work<String>() {
            public String run() throws IOException {
                String problem = ZipVerify.problem(new StickFs(act, root, cli), item.relPath, item.size, item.sha256);
                if (problem != null) return problem;
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

    /** The checked rows, loose and catalog, keyed by relPath. */
    private List<Pick> picks() {
        List<Pick> out = new ArrayList<Pick>();
        for (StickZip z : zips) {
            if (z.meta == null || !checked.contains(z.relPath)) continue;
            out.add(new Pick(z.relPath, z.meta.name, z.meta.version,
                    StatusLabeler.label(z, installed) != ZipStatus.NEW, CompatCheck.LOOSE.colour));
        }
        for (CatalogView v : views) {
            if (v.trust != CatalogView.Trust.TRUSTED) continue;
            for (CatalogZip z : v.zips) {
                if (!z.installable() || !checked.contains(z.relPath)) continue;
                out.add(new Pick(z.relPath, z.entry.name, z.entry.version,
                        StatusLabeler.label(z.zip, installed) != ZipStatus.NEW, verdictOf(z).colour));
            }
        }
        return out;
    }

    private void installSelected() {
        if (refuseWhileRunning()) return;
        final String root = stick;
        final List<Pick> picked = picks();
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

    private void confirmQueue(final String root, List<Pick> picked) {
        final String[] paths = new String[picked.size()];
        final boolean[] updates = new boolean[picked.size()];
        List<String> lines = new ArrayList<String>();
        List<String> yellow = new ArrayList<String>();
        List<String> red = new ArrayList<String>();
        for (int i = 0; i < paths.length; i++) {
            Pick p = picked.get(i);
            paths[i] = p.relPath;
            updates[i] = p.update;
            lines.add(p.name + " " + p.version);
            if (p.colour == Colour.YELLOW) yellow.add(p.name);
            if (p.colour == Colour.RED) red.add(p.name);
        }
        String message = ScreenText.queueConfirm(lines, yellow, red.size());
        Runnable go = new Runnable() {
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
        };
        if (red.isEmpty()) {
            Ui.confirm(act, null, message, "Install", go, "Cancel", null);
        } else {
            Ui.confirmWithTicks(act, message, red, "Install", go);
        }
    }
}
