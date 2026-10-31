# hkg.package.manager: design

Status: Phase 1 released (v1.0.0); Phase 2 built, not yet tried on the car. Owner: the contributor who requested it (2023 US Ioniq 5,
`NEEV23.USA.`, firmware 260507, NEMESIS installed). Working folder: `hkg-plugin-manager`.
Audience: **any Gen5W head unit with NEMESIS** *(owner)*. On-car testing is on the owner's
Ioniq 5; other cars are best effort.
Sibling projects used as reference: `../hkg-climate-page` (an example NEMESIS package and
its build/test setup), `../hmg_nemesis` (NEMESIS docs and source), `../HMG-Leafia-Plus`
(Wi-Fi and install research).

Marking used below: *(owner)* = decided by the owner in the design conversation;
*(my call)* = decided by the designer, open to the owner's correction; **Left to the
builder** = deliberately open.

## Intent

NEMESIS installs one package per USB insert: the stick's root `NEMESIS.data` names one zip,
the handler asks Yes/No, installs it, and many packages then restart the head unit. Anyone
running several packages (the owner already has hkg-climate-page plus community packages)
re-copies the stick and re-inserts it for every package and every update, and NEMESIS
Manager's screens don't show versions or offer updates.

The owner wants a **package manager app on the head unit** that grows in three phases:

1. **Phase 1:** manage packages from a folder of NEMESIS zips on one USB stick: install
   one, several or all of them (surviving the restarts in between), see what is installed
   with versions, update, uninstall. Opened with a configurable button press.
2. **Phase 2:** an open, structured **catalog** format (static JSON + signature, hostable
   from a GitHub repo or any web folder), read by the manager from a USB stick, with
   compatibility shown in three colours; plus a desktop **CLI that mirrors a remote catalog
   onto a stick**.
3. **Phase 3:** the manager reads a remote catalog **directly over the car's Wi-Fi** in
   client mode; the CLI gains developer features (building, checking and signing catalogs).
   Outline only: designed when Phase 3 starts *(owner)*.

Each phase ships and is useful on its own. Phase 1 must not need Phase 2's catalog.

## Background: what NEMESIS gives us

From `../hmg_nemesis` docs 02, 04, 05 and `Sources/native/`:

- **CLI** `/system/xbin/nemesis`: `install [--update] <zip>`, `uninstall <id>`,
  `pkg-list` (lines `id|name|locked|types`, **no version**), `pkg-info <zip>`,
  `pkg-launch <id>`, `root [uid] [-c command]`, `log dump`.
- **Privileged channel**: any app uid can run these through `/dev/nemesis.sock` /
  `/data/nemesis/root.sock`; the watchdog runs them as root. `su.c` reads the caller's
  uid (`SO_PEERCRED`) but only logs it: there is no allowlist. Our app is an ordinary uid
  and gets root operations through this channel, never its own.
- **Package** = zip with `NEMESIS.data` (INI) at its root: `[nemesis]` `id`, `name`,
  `version` (free text), `types`, hook scripts; `[app.N]` (`kind=apk` is installed with
  `pm install -r`, with NEMESIS working around the OEM signer allowlist); `[service.N]`.
  Installed trees live in `/data/nemesis/apps/<id>/`, including that package's
  `NEMESIS.data`. The INI parser keeps unknown sections and keys, so extra metadata does
  not break NEMESIS.
- **USB handler** (locked `usb-handler` service): watches `/storage/usb0..2` and
  `/mnt/media_rw/usb*` for a root `NEMESIS.data` with `type=install|execute|open`. With
  Android up it asks Yes/No (60 s timeout); with Android down it runs without asking. It
  re-arms when the stick is unmounted or when **`NEMESIS.data`'s identity (inode, size,
  mtime) changes**, so writing that file while the stick is mounted triggers the handler
  again at once.
- **Button opening**: NEMESIS Manager opens on three presses of Navigation or Setup, by
  listening for `com.hkmc.intent.action.NAVI_SHORTCUT` / `SETUP_SHORTCUT` (presses more
  than 1.5 s apart restart the count; a repeat of the same press within 200 ms is ignored).
  The car's log shows the path: `MicomD` -> `ModeKeyDispatcher` -> foreground view ->
  service -> broadcast `com.hkmc.intent.action.NAVI_SHORTCUT`. A broadcast cannot consume
  the press: the button's normal action still runs.
- **Restarts**: many packages `exec reboot` at the end of `postinstall` (hkg-climate-page,
  wireless.projection, Leafia). hkg-climate-page also showed that an update can need a
  restart between the old version's uninstall and the new install (the replaced APK held
  `/system` space until the restart). `/system` has ~265 MB free on the owner's car.
- **Platform**: Android 4.4.2 (API 19), Dalvik, mksh/toolbox shell (no `printf`, `tail`,
  `head`, `awk`, `[[ ]]`). The stock launcher does not list third-party apps.

## Goals

- Install, update and uninstall NEMESIS packages from the head unit's screen without
  re-copying the stick for each package.
- A multi-package install that survives any number of head unit restarts and ends with a
  clear per-package report.
- Show installed packages with their versions.
- Phase 2: an open catalog format anyone can host and sign; compatibility shown at a
  glance; a CLI to put a remote catalog on a stick.
- Phase 3: browse and install from a remote catalog over Wi-Fi.

## Non-goals

- **No compatibility checks in Phase 1** *(owner)*: the user is assumed to know what they
  are doing and to have checked their packages.
- **No changes to NEMESIS itself.** The manager only uses the CLI and its privileged
  channel. It does not replace NEMESIS Manager, which keeps its own screens (services,
  logs, wipe, Engineering Mode).
- **The manager never writes `/system` itself.** Only the packages it installs do.
- **No per-developer package signing.** Trust comes from the catalog's signature plus
  per-zip hashes *(owner, "for now")*.
- **Hosting the community catalog is not part of this design.** The format is the
  deliverable; who runs the shared instance is decided separately.
- **No developer tooling before Phase 3** *(owner)*.

## Anti-goals (failure even if it "works")

- A multi-install that loses track after a restart: installs a package twice, silently
  skips one, or reports something as installed when it is not.
- The manager carrying its own root path or `su` instead of going through NEMESIS.
- A catalog format tied to one host (GitHub-only URLs, an API server, accounts).
- A stick-derived string (file name, package field) reaching a root shell unquoted.
- A red (known incompatible) package installable with a single tap.

## Constraints

- Plain Java against `android-19`, no third-party libraries, same toolchain and layout
  as `../hkg-climate-page` (`tools/build-app.sh`, `tools/build-package.sh`, host tests,
  `private/` for keys and car captures) *(my call)*. Scripts are mksh/toolbox-safe and
  tested under `mksh` where available.
- **No model or firmware gate** *(owner: any Gen5W with NEMESIS)*. The manager installs and
  runs on any unit where NEMESIS runs. The only thing it requires is `/system/xbin/nemesis`
  and a working privileged channel. Its install refuses with a message if either is missing.
  Layouts adapt to the screen width instead of being drawn for one display. Features that
  depend on the car (which buttons broadcast) offer only what has been confirmed.
- No per-car secrets collected or published (VIN, serials, accounts, keys, `/data`
  contents beyond NEMESIS's own package files). No manufacturer files in the repo.
- **Writing to the stick from an ordinary app may be refused.** Android 4.4 limits
  third-party writes to secondary storage. Every write to the stick goes through one helper
  that tries a direct write and falls back to the NEMESIS root channel (write to the app's
  own files, then a root copy) *(my call)*. Which path works is confirmed on the car.
- **Shell safety**: any command run through `nemesis root -c` quotes every argument
  through one quoting function. Package ids are already restricted by NEMESIS
  (`[A-Za-z0-9._-]`, max 64, no leading `.`, no `..`); file names on the stick are not, so
  a zip whose name the quoting cannot handle safely is copied to a staging name first
  (**left to the builder**: copy or reject, once the CLI's handling of spaces and quotes is
  checked on the car; NEMESIS's own catalog has `Enable ADB.zip`).
- FAT/exFAT sticks: each write to a queue or state file is write-to-temp, `fsync`, rename,
  then directory sync, so a restart mid-write leaves the old or the new file, never a
  truncated one.

## Identity

- NEMESIS package id: **`hkg.package.manager`** *(owner)*. Display name "HKG Package
  Manager" *(my call)*.
- Android package name: **`hkgpackagemanager.app`** *(my call: `package` is a Java keyword,
  so `hkg.package.manager` cannot be the Android package name; follows hkg-climate-page's
  `hkgclimate.app`)*.
- Installed like any NEMESIS package: a stick with `type=install` and the manager's zip.
  Its `NEMESIS.data` has `[app.0] kind=apk`, `package=hkgpackagemanager.app` and a
  `component` for `pkg-launch`. Its `postinstall` starts the app's service once, because
  Android 4.4 sends no broadcasts to an app that has never run (it would miss the button
  presses).

### Where the manager keeps its state *(my call)*

NEMESIS updates a package by running `pm uninstall` without `-k` and deleting
`/data/nemesis/apps/<id>/`. A self-update would therefore wipe both the app's own data
directory and its package tree. Settings (button, press count) and, from Phase 2, the
trusted catalog keys live in **`/data/nemesis/hkg.package.manager/`**, written and read
through the root channel. This follows the existing convention: hkg-climate-page keeps
`/data/nemesis/hkg.climate.page.log`, and packages keep backups in
`/data/nemesis/backup/<id>/`. NEMESIS's own files are never touched. The folder survives
updates. The app keeps a cached copy in its own files for quick reads.

The manager's uninstall scripts do **not** remove this folder. `nemesis install --update`
runs the old version's `preuninstall` and `postuninstall` before installing the new one
(`../hmg_nemesis/Sources/native/pkg.c`, the `have_existing && update` block), and a script
can't tell an update from a real uninstall. Removing the folder there would wipe the
settings on every self-update. A real uninstall therefore leaves this small folder behind.
A later reinstall picks the settings up again, and Wipe NEMESIS removes it with the rest of
`/data/nemesis` *(my call, revised while planning)*.

The log stays in the app's own files and may be lost on a self-update. That's acceptable:
the queue results are on the stick.

---

## Phase 1: package manager from a stick of zips

### Opening the manager

- **Configurable button** *(owner)*: in Settings the user picks the button and, for a
  pressed button, the number of presses. The manager counts presses the way NEMESIS
  Manager does (1.5 s window, 200 ms repeat filter).
- Default: **hold Media** *(owner, 2026-10-31, after the key capture)*. Holding Media
  sends `com.mobis.intent.action.MEDIAGATE_SHORTCUT` about 0.8 s into the hold, so one
  broadcast opens the manager. The key capture showed:
  - **A short Media press isn't usable.** It sends the current media source's mode
    shortcut, which depends on state (`GPM_SHORTCUT` with Android Auto connected). The same
    action is also re-sent at boot to restore the mode.
  - **Tune Enter, the owner's first choice, sends no broadcast at all.** That was true for a
    press, a hold and a triple press alike.

  The receiver also ignores any shortcut broadcast whose
  `com.hkmc.mode.EXTRA_RESTOREMODE_REASON` isn't 0 *(my call)*. Press count is 2-5 for a
  pressed button *(my call)*. Findings: `docs/notes/key-capture.md` and
  `docs/notes/gen5w-hardware-keys.md`.
- Buttons offered: those whose broadcast an app is confirmed to receive. That's Off, press
  Navigation (`com.hkmc.intent.action.NAVI_SHORTCUT`, which NEMESIS Manager may also use)
  and hold Media. The framework also knows Setup, the custom (often ☆) key on the
  head unit and on the steering wheel (`com.mobis.intent.action.CUSTOM_SHORTCUT`,
  `SWRC_CUSTOM_SHORTCUT`), voice/PTT, Phone, Mode, Map, Display, Radio and Apps (many with
  `_LONG` versions). Some of these may not exist on a given model. Each is added only
  after a key-capture kit run shows it on a car *(my call)*. The custom key is the most
  promising extra, because the owner can assign its stock function.
- If the choice matches NEMESIS Manager's (Navigation or Setup), Settings warns that both
  apps will open. **Left to the builder**: where NEMESIS Manager keeps its choice; if it
  can't be read, show a general note instead.
- Also opened by `nemesis pkg-launch hkg.package.manager` and by the resume script (below).
  Whenever the manager opens, it checks every mounted stick for an unfinished queue.
- It also checks when a stick is mounted while it runs (media-mounted broadcast).

### Screens

**Left to the builder** (layout and style), within these contents. The screen is wide and
landscape. Text must be readable at arm's length, and targets large enough to tap.

1. **USB drive**: the zips found on the stick. Each row shows name, id and version (from
   the zip's `NEMESIS.data`) and a status:
   - **New**: id not installed;
   - **Update**: installed, showing "installed X, drive Y";
   - **Same version**: installed version equals the zip's.

   Versions are free text, so the manager shows both and never decides which is newer
   *(my call)*. Each row has **Install** (or **Update**) and a checkbox; the bar has
   **Select all** and **Install selected**. A zip without a readable `NEMESIS.data` or
   with an invalid id is listed greyed out with the reason.
2. **Installed**: every package from `nemesis pkg-list`, with name, id, version (read
   from `/data/nemesis/apps/<id>/NEMESIS.data` through the root channel), types and
   locked state. Each row has **Uninstall**. Locked packages (`usb-handler`) and the
   manager itself have no Uninstall *(my call: uninstall the manager from NEMESIS
   Manager)*.
3. **Progress / summary**: the queue while it runs (each package: waiting, installing,
   done, failed with reason), then the final report.
4. **Settings**: opening button and press count, and a "copy log to USB drive" button.

### Finding zips

- Stick roots checked: `/storage/usb0`, `usb1`, `usb2` (the same set NEMESIS watches).
  With more than one stick mounted, the user picks one *(my call)*. A multi-install queue
  needs that stick to be the only mounted one with a root `NEMESIS.data`, because
  NEMESIS's handler acts on the first stick root where it finds one. Otherwise the manager
  asks the user to remove the other stick first *(my call)*.
- Zips are looked for in the stick's root and in `packages/` (the folder NEMESIS's own
  `type=install` examples use), not deeper *(my call)*.
- A zip qualifies if its root has `NEMESIS.data` with a valid `[nemesis] id`. The manager
  reads it with `java.util.zip`. **Left to the builder**: whether to cross-check with
  `nemesis pkg-info`.

### Single install *(owner)*

- The user taps Install or Update on one zip and confirms. The manager runs
  `nemesis install <zip>` (or `install --update` for an installed id). Nothing is written to
  the stick. If the package restarts the head unit, that's expected.
- Result: from the CLI's output and exit code, then confirmed with `pkg-list`. Shown as a
  message. If the head unit restarts, the next time the manager opens it doesn't report
  that install *(my call: a restart is the package's normal behaviour, and the Installed
  screen shows the outcome)*.

### Multi-package install (the queue) *(owner)*

The user ticks several zips (or Select all) and taps Install selected. After one
confirmation listing them, the manager:

1. Writes a **queue file** to the stick (the "todo list") listing each zip in order with
   its state (`waiting`, `installing`, `done`, `failed`), plus its id, version and a
   reason/message field.
2. Writes the **resume descriptor**: a root `NEMESIS.data` with `type=execute` and `path`
   pointing at a small resume script on the stick. If the stick already had a root
   `NEMESIS.data`, it is first moved into the manager's folder and put back when the queue
   ends *(my call)*.
3. Runs the queue in order. For each package it saves `installing`, runs the install,
   saves `done` or `failed`, and moves on. **Failed packages are skipped and the queue
   carries on** *(owner)*.
4. At the end it shows the **final report** *(owner)*: one line per package with its result
   and reason. It then removes the resume descriptor, puts back any original one, and
   keeps the queue file on the stick as a record, marked finished *(my call)*.

**After a restart**: the stick is still in, so NEMESIS finds the resume descriptor and asks
Yes/No. That prompt cannot be skipped while Android is up. On Yes the resume script opens
the manager in resume mode. If NEMESIS runs the script before Android has finished starting,
the script waits for startup before opening the manager *(my call)*. The manager reads the
queue:

- An item left at `installing` counts as **done** if `pkg-list` lists its id and its
  installed `NEMESIS.data` has the queued version. Otherwise it counts as **failed**, with
  the reason "interrupted (head unit restarted or drive removed)". It is not retried
  automatically *(my call: retrying a half-run install hook blind is riskier than
  reporting it)*.
- It then continues with the next `waiting` item.
- The same check runs when the manager is opened any other way and finds an unfinished
  queue. It first shows "Unfinished install found: continue?" with the list *(my call)*.

**Ordering rules** *(my calls)*:

- The user's order is kept, except that an update of **`hkg.package.manager` itself always
  runs last**: `pm install -r` of our own APK kills our process. The new version's
  `postinstall` starts its service. The service finds the queue with that last item at
  `installing`, resolves it with the rule above, and shows the final report.
- One queue at a time. Starting a new one while an unfinished queue exists means
  continuing or discarding it first.

**The handler re-arm side effect**: writing the resume descriptor while the stick is
mounted changes its identity, so NEMESIS's handler fires right away and shows its Yes/No
while the queue is starting. Either answer must be harmless. The resume script and the
manager's resume path do nothing if a queue is already running in the app (a lock the app
holds while running). The progress screen tells the user that if NEMESIS asks to run an
item from the USB drive, either answer is fine *(my call)*. **Left to the builder**:
writing the descriptor in a way that avoids the extra prompt, if one exists.

**Robustness**:

- The queue runs in a foreground service, not the activity, so leaving the screen does
  not stop it *(my call)*.
- Pulling the stick mid-queue: the current install likely fails (its zip is gone). The
  queue can't be saved without the stick, so it stops. On the next insert, the
  `installing` item is resolved with the rule above. The progress screen says not to remove
  the drive.
- The queue file names zips by path relative to the stick, plus each zip's size and
  SHA-256 at queue time. A zip that changed since then is reported failed ("changed on the
  drive") instead of installed *(my call)*.

**Stick layout** *(my call, names left adjustable by the builder)*:

```text
NEMESIS.data                 resume descriptor while a queue runs (type=execute)
HKGPM/resume.sh              opens the manager in resume mode
HKGPM/queue.json             the todo list (org.json is built into Android 4.4)
HKGPM/NEMESIS.data.saved     the stick's original descriptor, if there was one
HKGPM/log.txt                copied log (Settings > copy log)
*.zip, packages/*.zip        the packages
```

### Uninstall *(owner)*

One package at a time, after a confirmation. Runs `nemesis uninstall <id>` and shows the
output. If the package's uninstall says a restart finishes it (hkg-climate-page does), the
manager says so. *(My call: no uninstall queue in Phase 1.)*

### Update *(owner)*

An update is an install of a stick zip whose id is already installed (`install --update`),
alone or in a queue. No separate update source in Phase 1.

`install --update` removes the old version and installs the new one in one call. If the
new install fails, the package is no longer installed. hkg-climate-page hit this: `/system`
space is freed only after a restart. The manager doesn't try to prevent this *(owner)*.
After a failed update it checks `pkg-list`. If the id is gone, the result says "was
uninstalled; restart the head unit and install it again". The manager never restarts the
head unit itself.

### Logging

The manager keeps its own log (every CLI call, its output and exit code, queue
transitions) in its app files. Settings copies it to the stick. Queue results are also in
`queue.json`. No per-car secrets go in either.

### Testing (Phase 1)

- Host tests (JUnit, as in hkg-climate-page) for: zip/INI parsing, status labelling,
  queue state transitions, resume resolution of `installing` items, ordering rules, shell
  quoting, atomic writes. The CLI runner is behind an interface with a fake NEMESIS.
- `resume.sh` tested under `mksh` with stubbed `getprop`/`am`.
- An on-car checklist (like `../hkg-climate-page/docs/testing/`) covering at least:
  - single install;
  - a three-package queue where the middle package restarts the head unit;
  - a queue with a deliberately failing package;
  - pulling the stick mid-queue;
  - self-update in a queue;
  - the extra handler prompt;
  - both stick-write paths;
  - button opening with each choice.

---

## Phase 2: catalog format, catalog on a stick, mirror CLI

### The catalog *(owner)*

A **static folder**: a JSON index, a detached signature, and package zips (stored beside
it or linked by URL). It can be served from a GitHub repo, GitHub Pages or any plain web
server, and copied to a stick byte for byte. The format is **fully open and documented**
(a spec document plus a JSON Schema in this repo) so that anyone can run a catalog. A
shared community catalog may take new packages by merge requests. That process belongs to
the community catalog's repo, not to this design.

**Index contents** (field names **left to the builder**; meaning fixed here):

- Format version (integer; the manager refuses a newer major version with a clear
  message), catalog name, catalog id, publisher, generated-at time, the signing key's id.
- One entry per package id *(my call: one current version per id per catalog)*:
  - id, name, version, short description, author, homepage (optional);
  - zip location (relative to the index, or an absolute URL), size, SHA-256;
  - optional compatibility (below).
- Unknown fields are ignored, so the format can grow.

**Signature**: a detached signature over the exact bytes of the index file. *(My call:
the algorithm must be verifiable on Android 4.4 with the platform's own crypto, no extra
libraries. **Left to the builder** to pick, e.g. ECDSA P-256 or RSA, after checking what
API 19 on the car verifies.)* The zips are covered by the hashes in the signed index.

### Trust *(owner, "for now")*

- The manager keeps a list of trusted catalog keys. A catalog signed by a trusted key is
  shown. A catalog whose signature fails is **not shown** (an error says why). A zip whose
  size or hash doesn't match is refused.
- A catalog signed by an unknown key shows only "untrusted catalog, key fingerprint
  …", with an option to trust that key after the user confirms the fingerprint *(my
  call)*. Keys can also be added from a key file on the stick, and removed in Settings.
- *(My call)* No keys are built in at first. The community catalog's key may become a
  default once that catalog exists.

### Compatibility metadata *(owner)*

Optional. A package may give **positive** ("works on") and **negative** ("does not work
on") compatibility.

*(My calls, because these meanings go into the published format.)*

- Each side is a list of rules. A side matches if **any** of its rules matches (OR across
  rules).
- A rule has one or both of these conditions, and **all** the conditions it has must match
  (AND within a rule). A rule with no conditions is invalid and ignored.
  - **Build**: a glob pattern matched against the car's build id (`ro.build.product`, e.g.
    `NEEV23.USA.0000.V015.012.260507`). `*` matches any run of characters and `?` one
    character. The whole string must match, case-sensitive.
  - **Hardware**: a list of names, each required present (`wifi`) or required absent
    (`!wifi`). Phase 2 defines only `wifi`, detected from the Wi-Fi driver's result the way
    Leafia's `wifi_hw` does. A rule naming hardware the manager doesn't know never matches.
    That means it can't make a package green or red.
  - A hardware-only rule is valid ("works on any car with Wi-Fi"; "doesn't work without
    Wi-Fi" is a negative rule with `!wifi`).
- **Conflicts** are a separate list of package ids on the negative side. Having any of
  them installed counts as a negative match.

**The three states** *(owner)*:

| State | When | Install |
|---|---|---|
| **Green** check | the car matches a positive rule | normal |
| **Yellow** warning | no compatibility data, or no match either way | after a confirmation prompt |
| **Red** | the car matches a negative rule | only after ticking a checkbox, then pressing install |

*(My call)* Red wins over green when both match. In a multi-install, the one queue
confirmation covers the yellow packages (each named). Each red package needs its own
checkbox ticked before the queue can start.

### The manager with a catalog on the stick

- Catalogs live on the stick at `HKGPM/catalogs/<catalog-id>/` (index, signature, zips)
  *(my call: more than one catalog per stick allowed; each is checked with its own key;
  the same id from two catalogs is shown twice, labelled with the catalog name)*.
- The **USB drive** screen shows catalog packages with their colour, description and
  new/update/same status, and keeps Phase 1's loose zips in a separate section marked "not
  from a catalog". Loose zips are **yellow** (no metadata, no hash) *(my call)*.
- Installs and queues work exactly as in Phase 1. The hash is checked just before each
  install, not only at listing.

### Mirror CLI *(owner: mirroring only in Phase 2)*

A desktop command-line tool, Python 3 *(my call: matches NEMESIS's and Leafia's tooling;
prefer the standard library; the signature check may need one well-known crypto package,
**left to the builder**)*:

- Input: a catalog URL (or local path) and a stick path; optionally a list of package ids
  to fetch (default all) *(my call)*.
- Downloads the index and signature unchanged. Verifies the signature against a key the
  user passes or has stored. Downloads each wanted zip, verifies size and SHA-256, and
  writes everything in the stick layout above.
- Mirroring a subset keeps the full signed index on the stick. The manager shows entries
  whose zip is missing as "not on this drive".
- Re-running updates the stick in place (new index, new or changed zips; removes zips the
  index no longer lists). Download resume and parallelism are **left to the builder**.

### Testing (Phase 2)

- Host tests for parsing, the glob matcher, the colour rules, signature and hash checks
  (with test keys), and a mirror run against a local test catalog.
- A sample catalog and test key in the repo (test key only, clearly marked).
- An on-car checklist for:
  - a trusted catalog;
  - a tampered index;
  - a tampered zip;
  - an unknown key;
  - each colour, including a red install;
  - a mixed queue.

---

## Phase 3: Wi-Fi and developer tools (outline only)

How Phase 3 works is decided when it starts *(owner)*. Recorded here as inputs, not
decisions:

- **Goal**: the manager reads a remote catalog (same format, same signature and hash
  checks, same colours) directly over the car's Wi-Fi in client mode, and installs from
  it with the same queue. Downloaded zips are cached on the stick or in `/data` (to be
  decided then).
- **Developer tools**: the CLI gains catalog building, checking (schema, hashes,
  compatibility lint) and signing.
- **Known facts and risks** (from Leafia's notes and the owner's car log):
  - US TCC803x units have a Broadcom SDIO Wi-Fi chip. The owner's car runs `hostapd` and
    `HotspotService` (the stock hotspot).
  - Hyundai's "client + wireless" mode is `persist.wifi.state=3`, with
    `android.hardware.wifi.xml` added. Setting it changes `/system` and needs a restart.
    The kernel reports `op_mode=0x0005` (client + Wi-Fi Direct) and no `rsdb_mode`, so
    client, hotspot and wireless CarPlay/AA would share one channel. Running them together
    is untested.
  - No station-mode activity was seen in the car's log; joining a home network is unproven
    on any car.
  - The car's own modem didn't bring up data for apps (`TYPE_MOBILE_HIPRI` failed) and is
    probably limited to Hyundai's servers.
  - Android 4.4 has TLS 1.2 off by default for app sockets and an old CA store. The
    signed index and hashes keep packages safe whatever the transport, but HTTPS to modern
    hosts needs a TLS setup the app ships itself.
  - The NEMESIS author has announced an app store (xwtk.cloud). Coordinating on the catalog
    format before Phase 3 is worthwhile.
- **Suggested first step**: a read-only kit, then a small test package, to confirm client
  mode joins a network on the owner's car and to see its effect on the hotspot and on
  wireless projection.

## To-do

1. **Read-only key-capture kit** *(owner, 2026-10-30)*. **Done 2026-10-31:** two runs, and
   the default is now hold Media (see "Opening the manager"). This came first in Phase 1,
   because the opening button depends on it.
   - **How it works:** a `type=execute` stick in the style of the hkg-climate kit (mksh-safe,
     changes nothing on the car). The owner presses each candidate button (Media, Navigation,
     the custom/☆ key, steering-wheel keys, short and long presses), then runs the kit
     within the log's window.
   - **What it records:** the `ModeKeyDispatcher` key lines and the
     `com.hkmc.intent.action.*` / `com.mobis.intent.action.*` broadcasts each press
     produced, with the action strings, written to the stick.
   - **What it leaves out:** no other log content (filtered to key and shortcut lines, so no
     phone names or addresses).
   - **Its findings set:** the Media default (or the Navigation fallback) and the list of
     buttons Settings offers.

## Left to the builder (summary)

- Screen layouts and visual style (Phase 1 contents are fixed above).
- Exact file and field names in the stick layout and the catalog index (meanings fixed).
- The signature algorithm, within "verifiable on Android 4.4 without extra libraries".
- Whether zips with awkward file names are copied to a staging name or rejected.
- Where NEMESIS Manager stores its button choice (for the conflict warning).
- Avoiding the extra handler prompt when the resume descriptor is written, if possible.
- Cross-checking zips with `nemesis pkg-info`; CLI download resume and parallelism; the
  CLI's crypto dependency.

## Owner decisions (2026-10-30)

- Three phases as above. Phase 3 design deferred.
- Phase 1 assumes an expert user; no compatibility checks.
- Single install: just install. Multi-install: a todo list on the stick, updated as each
  package starts and finishes, plus a NEMESIS descriptor that reopens the manager after a
  restart. The manager checks for an unfinished list on start and on insert.
- Failed packages are skipped; a final status report is shown at the end.
- Phase 1 also lists installed packages with versions, uninstalls, and updates.
- Opened by a configurable car button; default hold Media (set after the key capture).
- To-do: a read-only key-capture kit to confirm which buttons can open the manager.
- Catalog: static page + JSON, hostable from a GitHub repo; a community catalog may grow
  by merge requests; the format is fully open so anyone can run their own.
- Trust: per-zip SHA-256 in a catalog signed by its operator; the car trusts the catalog
  keys the user adds.
- Compatibility: optional positive and negative metadata; green / yellow / red as in the
  table above.
- The Phase 2 CLI only mirrors; developer features wait for Phase 3.
- Package id `hkg.package.manager`.
- Audience: any Gen5W with NEMESIS; no model or firmware gate; on-car testing on the
  owner's Ioniq 5, others best effort.
- A failed `install --update` that leaves the package uninstalled is reported with a
  "restart and install again" hint; the manager doesn't split updates or restart the unit.
