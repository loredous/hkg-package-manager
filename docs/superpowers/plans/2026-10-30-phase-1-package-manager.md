# hkg.package.manager Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A NEMESIS package (`hkg.package.manager`) whose app installs, updates and uninstalls NEMESIS packages from a stick of zips. A multi-install queue on the stick survives head unit restarts. The app opens on a configurable button press. Built after a read-only key-capture kit confirms the button.

**Architecture:** Pure-Java core (`core/src`, host-tested with JUnit) holds every decision:
- INI and zip parsing;
- status labels;
- the queue file and its state machine;
- the resume descriptor swap;
- shell quoting;
- press counting;
- settings.

A thin Android layer (`app/src`, API 19, views built in code, no resources) supplies the real NEMESIS CLI runner, the stick file system, the foreground queue service, the receivers and the screens. Shell pieces (kit, `stick/resume.sh`, package hooks) are mksh/toolbox-safe and tested on the host with stubs. The layout and tooling mirror `../hkg-climate-page`.

**Tech Stack:** Java 8 source compiled against `android-19`, `org.json` (built into Android; Maven jar for host tests), d8/aapt/zipalign/apksigner from build-tools 30.0.3, JUnit 4.13.2, POSIX sh/mksh for kit, hooks and tests, NEMESIS CLI `/system/xbin/nemesis`.

**Spec:** `docs/superpowers/specs/2026-10-30-hkg-package-manager-design.md` (Phase 1 and To-do sections; Phases 2-3 are out of scope for this plan).

## Global Constraints

- NEMESIS id `hkg.package.manager`, display name `HKG Package Manager`, Android package `hkgpackagemanager.app`. Java packages: `hkgpackagemanager.core`, `hkgpackagemanager.app`.
- `minSdkVersion` and `targetSdkVersion` 19. No third-party libraries in the APK.
- All root work goes through `/system/xbin/nemesis` (argv to the daemon). The app never ships `su` or its own root path.
- Only `nemesis root -c <command>` goes through a shell. Every argument inside that command is built with `ShellQuote.quote`. Install and uninstall are passed as argv, never through a shell.
- The daemon limits each argument to 768 bytes and output to 512 KiB (`../hmg_nemesis/Sources/native/su.c:11-13`).
- No model or firmware gate. The only requirement is `/system/xbin/nemesis` with a working daemon channel.
- Head unit shell scripts: no `printf`, `tail`, `head`, `seq`, `awk`, `grep` or `[[ ]]`. Use `echo`, `read` loops, `case` and `[ ]`. Remount only with `nemesis remount`. (This manager never remounts.)
- Stick paths (relative to the stick root): `NEMESIS.data`, `HKGPM/resume.sh`, `HKGPM/queue.json`, `HKGPM/NEMESIS.data.saved`, `HKGPM/log.txt`.
- State folder: `/data/nemesis/hkg.package.manager/` (settings). The uninstall scripts never remove it (spec, "Where the manager keeps its state").
- Stick roots: `/storage/usb0`, `/storage/usb1`, `/storage/usb2`.
- No per-car secrets in logs or the kit output. No manufacturer files in the repo. Car captures go in `private/` (git-ignored).
- Commits: plain-words message, ending with the two attribution lines from the session (`Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>` and the `Claude-Session:` line).
- Exact user-facing strings (used in code and tests verbatim):
  - `MSG_INTERRUPTED` = `interrupted (head unit restarted or drive removed)`
  - `MSG_CHANGED` = `changed on the drive`
  - `MSG_MISSING` = `missing from the drive`
  - `MSG_UPDATE_GONE` = `was uninstalled; restart the head unit and install it again`
  - `MSG_DISCARDED` = `not installed (list discarded)`
  - `MSG_PATH_TOO_LONG` = `path too long for NEMESIS`
  - install failure: `install failed: ` + last non-empty output line (or `exit <n>` if there's no output)
  - version mismatch: `installed version is <got>, expected <want>`
  - zip problems:
    - `not a readable zip`
    - `no NEMESIS.data at the zip's root`
    - `NEMESIS.data is inside a folder in the zip, not at its root`
    - `this is a USB install stick, not a package: copy its packages/ zip instead`
    - `no package id in NEMESIS.data`
    - `invalid package id: <id>`
  - handler note on the progress screen: `If NEMESIS asks to run an item from the USB drive, either answer is fine.`
  - resume prompt: `Unfinished install found: continue?`

## Review Focus

1. **macOS junk and our own folder on the stick.** `._foo.zip`, `.Trashes` and `HKGPM/` are ignored, never listed as invalid packages. Test: `StickScannerTest.ignoresDotFilesAndOwnFolder` (Task 3).
2. **Zip names with spaces, quotes, `$` or non-ASCII** (NEMESIS's own `Enable ADB.zip`). They're listed and installed through argv; any `root -c` string quotes them safely. Tests: `NemesisCallsTest.installPassesPathAsOneArgument`, `ShellQuoteTest.quotesSingleQuotesAndDollar` (Task 4).
3. **Stick pulled mid-queue.** Saving the queue fails, so the runner stops without starting another install, and the outcome says so. Test: `QueueRunnerTest.saveFailureStopsBeforeNextInstall` (Task 7).
4. **Corrupt, truncated or newer-format queue file.** It reads as "unreadable", never throws to the UI, and the user can discard it. Test: `QueueCodecTest.rejectsTruncatedAndNewerFormat` (Task 5).
5. **A bundle zip (outer `type=install` descriptor) or a zip with a wrapping folder.** It's greyed out with its specific reason, not a generic error. Tests: `StickScannerTest.bundleZipGetsBundleReason`, `StickScannerTest.wrappedFolderGetsFolderReason` (Task 3).

---

## File Structure

```text
.gitignore  CLAUDE.md  README.md
kit/NEMESIS.data  kit/NEMESIS.sh  kit/README.txt          key-capture kit (Task 1)
tools/lint-sh.sh                                           mksh-safety lint shared by builds
tools/build-kit.sh  tools/toolchain.sh  tools/test-java.sh
tools/build-app.sh  tools/build-package.sh
core/src/hkgpackagemanager/core/
  IniFile PackageId PackageMeta BadPackageException        Task 2
  StickZip StickScanner PkgListEntry PkgList InstalledPackage ZipStatus StatusLabeler   Task 3
  ShellQuote CliResult Nemesis NemesisCalls RestartHint    Task 4
  ItemState QueueItem Queue QueueCodec QueueOrder Messages Task 5
  StickFiles StickLayout DescriptorSwap QueuePreflight     Task 6
  ZipFingerprint ZipCheck QueueStore InstallStep QueueRunner QueueSession  Task 7
  Button Settings SettingsCodec PressCounter               Task 8
core/test/hkgpackagemanager/core/  *Test.java + FakeNemesis, MemStickFiles (test helpers)
stick/resume.sh                                            Task 6
app/AndroidManifest.xml
app/src/hkgpackagemanager/app/
  NemesisCli StickFs Sticks AppLog StateStore              Task 9
  QueueService ButtonReceiver MountReceiver                Task 10
  MainActivity DriveScreen InstalledScreen ProgressScreen SettingsScreen  Task 11
package/NEMESIS.data  package/scripts/{common,pre,post,preun}.sh   Task 12
tests/kit/run.sh tests/resume/run.sh tests/package/run.sh tests/app/check-app.sh (+ stubs/)
docs/notes/key-capture.md  docs/testing/2026-10-30-phase-1.md
```

---

### Task 1: Repo start and read-only key-capture kit

The opening button depends on this kit's result, so it comes first. The owner runs the kit on the car; Task 8 needs its findings.

**Files:**
- Create: `.gitignore`, `CLAUDE.md`, `README.md`, `kit/NEMESIS.data`, `kit/NEMESIS.sh`, `kit/README.txt`, `tools/lint-sh.sh`, `tools/build-kit.sh`, `tests/kit/run.sh`, `tests/kit/stubs/{logcat,dumpsys,am,getprop,sleep}`

**Interfaces:**
- Produces:
  - `tools/lint-sh.sh <dir>...` exits 1 and lists offending lines when any `*.sh` under the dirs uses `printf`, `tail`, `head`, `seq`, `awk`, `grep` or `[[`. Reuse the command-position regexes from `../hkg-climate-page/tools/build-package.sh:21-36`, adding `awk` and `grep`.
  - `dist/hkg-keycap-kit.zip`.
  - On the stick, the kit writes `KEYCAP/keys.txt`, `KEYCAP/broadcasts.txt` and `KEYCAP/summary.txt`.

- [ ] **Step 1: Start the repo.** Run `git init -b main` in the working folder (the spec is already in `docs/`).
  - `.gitignore`: `build/`, `dist/`, `private/`.
  - `CLAUDE.md`: project rules adapted from `../hkg-climate-page/CLAUDE.md`. Keep: kit shell rules, private files, no secrets, commit style. Drop the climate-specific vehicle-command rules. Add "all root work through the NEMESIS CLI" and "uninstall never removes `/data/nemesis/hkg.package.manager/`".
  - `README.md`: one paragraph and the build steps (fill in as tasks land).

- [ ] **Step 2: Write the failing kit test** `tests/kit/run.sh`, in the style of `../hkg-climate-page/tests/kit/run.sh`: `check` helper, temp drive, stubs on `KIT_TEST_PATH`.
  - The `logcat` stub prints fixture lines, including:
    - `V/ModeKeyDispatcher(  547): [Key] from Android - KeyEvent { action=ACTION_DOWN, keyCode=KEYCODE_SHORTCUT_MEDIA, scanCode=19, ...}`
    - a `sendBroadcastKeyEvent` line naming `com.hkmc.intent.action.NAVI_SHORTCUT`
    - `W/MicomD ( 174): [micomd] send key fd = 14, keycode: 0x0005 press: 1`
    - and two private lines: `D/BluetoothPbap: contact Jane Doe 555-0100` and `I/Bt: addr 00:11:22:33:44:55`.
  - The `dumpsys` stub prints fixture `Historical broadcasts` entries with `act=com.hkmc.intent.action.MEDIA_SHORTCUT` and `act=android.intent.action.TIME_TICK`.
  - The `sleep` stub returns at once. The `am` stub logs its arguments.

  Checks:
  - `keys.txt` contains the `KEYCODE_SHORTCUT_MEDIA` line, the `NAVI_SHORTCUT` line and the `MicomD` line;
  - `keys.txt` contains neither `Jane Doe` nor `00:11:22:33:44:55`;
  - `broadcasts.txt` contains `MEDIA_SHORTCUT` and not `TIME_TICK`;
  - `summary.txt` has a `product ` line and ends with `done`;
  - `am` was called with `com.hkmc.intent.action.TOAST` at least twice (start and finish);
  - `tools/lint-sh.sh kit` passes.

- [ ] **Step 3: Run it and confirm it fails.** Run `sh tests/kit/run.sh`. Expected: FAIL lines, because `kit/NEMESIS.sh` is missing.

- [ ] **Step 4: Write `kit/NEMESIS.data`** (`[nemesis]`, `version=1.0`, `type=execute`, `path=NEMESIS.sh`) **and `kit/NEMESIS.sh`.** The script:
  - uses PATH/LD_LIBRARY_PATH lines as in `../hkg-climate-page/kit/NEMESIS.sh`, finds the drive from `$0`, and honours `KIT_TEST_PATH`;
  - makes `KEYCAP/` and writes `product $(getprop ro.build.product)` to `summary.txt`;
  - saves `logcat -d -v time` (presses made just before insert) to a temp file under `/data/local/tmp`;
  - toasts `Key capture: press each button now (60 seconds)`;
  - runs `logcat -v time` in the background into a second temp file, then `sleep 60` and `kill`s it;
  - filters both temp files line by line into `keys.txt`, keeping only lines matching `*ModeKeyDispatcher*`, `*KEYCODE_*`, `*_SHORTCUT*` or `*"send key"*`;
  - filters `dumpsys activity broadcasts` into `broadcasts.txt`, keeping `*_SHORTCUT*` and `*CUSTOM*` lines;
  - deletes the temp files, writes `done`, syncs, and toasts `Key capture finished, unplug the drive`.
- [ ] **Step 5: Write `kit/README.txt`.** Steps for the owner:
  - Copy the zip contents to an empty FAT/exFAT stick.
  - Optionally press the buttons just before inserting.
  - Insert the stick and tap Yes.
  - When the toast appears, press each candidate button in a known order, short and long, writing the order down: Media, Navigation, the ☆/custom key if present, steering-wheel keys.
  - Wait for the finished toast.
  - Say what's kept: only key and shortcut lines; nothing else from the log.

  **Write `tools/build-kit.sh`** as in `../hkg-climate-page/tools/build-kit.sh`, with fixed timestamps, writing `dist/hkg-keycap-kit.zip`.
- [ ] **Step 6: Run the tests and confirm they pass.** Run `sh tests/kit/run.sh && tools/build-kit.sh`. Expected: every line `ok`, then the zip path printed.
- [ ] **Step 7: Commit** (`git add -A && git commit`) with the message "Add read-only key-capture kit".
- [ ] **Step 8: Owner checkpoint.** Ask the owner to run the kit and put `KEYCAP/` in `private/KEYCAP/`. Write `docs/notes/key-capture.md`, with no private log lines, recording:
  - the exact action string each pressed button broadcast;
  - whether it has a `_LONG` variant;
  - the owner's press order.

  Task 8 reads it. Tasks 2-7 don't depend on it and can go ahead meanwhile.

### Task 2: Java toolchain and NEMESIS.data parsing

**Files:**
- Create: `tools/toolchain.sh`, `tools/test-java.sh`, `core/src/hkgpackagemanager/core/{IniFile,PackageId,PackageMeta,BadPackageException}.java`
- Test: `core/test/hkgpackagemanager/core/{IniFileTest,PackageIdTest,PackageMetaTest}.java`

**Interfaces:**
- Produces:
  - `IniFile.parse(String text) -> IniFile`
  - `String get(String section, String key)`: first match, or null
  - `boolean hasSection(String section)`
  - `PackageId.isValid(String id) -> boolean`
  - `PackageMeta.from(IniFile ini) throws BadPackageException`, with public final `String id, name, version, types`
  - `BadPackageException(String reason)`, with `getMessage()` returning the reason

- [ ] **Step 1: Copy the toolchain.** Copy `../hkg-climate-page/tools/toolchain.sh`, trimmed to JUnit plus org.json (drop smali, venv and qemu).
  - Add the Maven Central entry `json org/json/json/20231013/json-20231013.jar <sha256>`. Download it once, check it against Maven's `.sha1`, and pin the sha256 you compute.
  - `env` exports `ANDROID_JAR`, `BUILD_TOOLS`, `JUNIT_CP`, `JSON_CP` and `MOCK_SH`.
  - Copy `tools/test-java.sh` (core/src + core/test, every `*Test`), with `$JSON_CP` added to the compile and run class paths. The APK never bundles this jar: Android has `org.json`.
  - Run `tools/toolchain.sh fetch && tools/toolchain.sh check`. Expected: `ok` lines for android.jar, build-tools, junit and json.

- [ ] **Step 2: Write the failing tests.**

```java
// IniFileTest
@Test public void parsesLikeNemesis() {
    IniFile ini = IniFile.parse("# c\n; c\n[nemesis]\r\n  id = my.tool \nname=A=B\n\n[app.0]\nkind=apk\n");
    assertEquals("my.tool", ini.get("nemesis", "id"));
    assertEquals("A=B", ini.get("nemesis", "name"));       // split on first '='
    assertEquals("apk", ini.get("app.0", "kind"));
    assertNull(ini.get("nemesis", "kind"));
    assertTrue(ini.hasSection("app.0"));
}
@Test public void firstDuplicateWins() { assertEquals("1", IniFile.parse("[n]\nk=1\nk=2\n").get("n", "k")); }
@Test public void ignoresLinesWithoutEqualsAndUnclosedSections() {
    IniFile ini = IniFile.parse("[n\nk=1\n[m]\njunk\nk=2\n");
    assertEquals("1", ini.get("", "k")); assertEquals("2", ini.get("m", "k"));
}
// PackageIdTest
@Test public void rules() {
    assertTrue(PackageId.isValid("hkg.climate.page")); assertTrue(PackageId.isValid("a_b-c.1"));
    assertFalse(PackageId.isValid("")); assertFalse(PackageId.isValid(".hidden"));
    assertFalse(PackageId.isValid("a..b")); assertFalse(PackageId.isValid("a/b"));
    assertFalse(PackageId.isValid("a b")); assertFalse(PackageId.isValid(repeat('a', 65)));
    assertTrue(PackageId.isValid(repeat('a', 64)));
}
// PackageMetaTest
@Test public void defaults() throws Exception {
    PackageMeta m = PackageMeta.from(IniFile.parse("[nemesis]\nid=x.y\n"));
    assertEquals("x.y", m.name); assertEquals("", m.version); assertEquals("", m.types);
}
@Test public void missingId() { assertReason("no package id in NEMESIS.data", "[nemesis]\nname=n\n"); }
@Test public void invalidId() { assertReason("invalid package id: ../x", "[nemesis]\nid=../x\n"); }
```

- [ ] **Step 3: Run the tests and confirm they fail.** Run `tools/test-java.sh`. Expected: compilation errors (classes missing).
- [ ] **Step 4: Implement the four classes.** `IniFile` mirrors `../hmg_nemesis/Sources/native/ini.c:26-57`:
  - split on `\n`;
  - trim each line, including `\r`;
  - skip lines that are empty or start with `#` or `;`;
  - a `[name]` line needs a closing `]`;
  - a `key=value` line splits at the first `=`, and both sides are trimmed.

  `PackageId` allows `[A-Za-z0-9._-]`, length 1-64, no leading `.` and no `..`.
- [ ] **Step 5: Run the tests and confirm they pass.** Run `tools/test-java.sh`. Expected: `OK (n tests)`.
- [ ] **Step 6: Commit** with the message "Add NEMESIS.data parsing and the Java test toolchain".

### Task 3: Finding zips on the stick and labelling them

**Files:**
- Create: `core/src/hkgpackagemanager/core/{StickZip,StickScanner,PkgListEntry,PkgList,InstalledPackage,ZipStatus,StatusLabeler}.java`
- Test: `core/test/hkgpackagemanager/core/{StickScannerTest,PkgListTest,StatusLabelerTest}.java`

**Interfaces:**
- Consumes: `IniFile`, `PackageMeta`, `BadPackageException` (Task 2).
- Produces:
  - `StickZip`: public final `String relPath; long size; PackageMeta meta; String problem` (exactly one of `meta` and `problem` is non-null).
  - `StickScanner.scan(File stickRoot) -> List<StickZip>`.
  - `PkgListEntry`: public final `String id, name, types; boolean locked`.
  - `PkgList.parse(String output) -> List<PkgListEntry>`.
  - `InstalledPackage`: public final `PkgListEntry entry; String version` (null when unreadable).
  - `enum ZipStatus { NEW, UPDATE, SAME, INVALID }`.
  - `StatusLabeler.label(StickZip zip, Map<String, InstalledPackage> installedById) -> ZipStatus`.

- [ ] **Step 1: Write the failing tests.** A test helper `writeZip(File, String... nameThenContent)` builds zips with `java.util.zip.ZipOutputStream`.

```java
// StickScannerTest
@Test public void findsRootAndPackagesFolderOnly() // a.zip, packages/b.ZIP, deep/c.zip -> relPaths ["a.zip","packages/b.ZIP"]
@Test public void ignoresDotFilesAndOwnFolder()   // ._a.zip, .Trashes/x.zip, HKGPM/q.zip -> nothing listed
@Test public void readsMeta()                      // NEMESIS.data id=x.y version=1.2 -> meta.id "x.y", meta.version "1.2", size == file length
@Test public void notAZip()                        // garbage bytes named a.zip -> problem "not a readable zip"
@Test public void noMeta()                         // zip with only readme.txt -> "no NEMESIS.data at the zip's root"
@Test public void wrappedFolderGetsFolderReason()  // tool/NEMESIS.data -> "NEMESIS.data is inside a folder in the zip, not at its root"
@Test public void bundleZipGetsBundleReason()      // NEMESIS.data "[nemesis]\ntype=install\npath=packages/x.zip\n" -> "this is a USB install stick, not a package: copy its packages/ zip instead"
@Test public void badIdReason()                    // id=a..b -> "invalid package id: a..b"
@Test public void spacesAndUnicodeInNames()        // "Enable ADB.zip", "naïve $x'.zip" -> both listed with meta
// PkgListTest
@Test public void parsesLinesAndSkipsJunk() {
    List<PkgListEntry> l = PkgList.parse("usb-handler|USB Handler|1|service\n\nhkg.climate.page|HKG Climate Page|0|\ngarbage\n");
    assertEquals(2, l.size()); assertTrue(l.get(0).locked); assertEquals("", l.get(1).types);
}
// StatusLabelerTest
@Test public void labels() // not installed -> NEW; installed "2.0.0" vs zip "2.0.0" -> SAME; " 2.0.0" vs "2.0.1" -> UPDATE;
                           // installed version null -> UPDATE; zip with problem -> INVALID
```

- [ ] **Step 2: Run the tests and confirm they fail.** Run `tools/test-java.sh`. Expected: compile errors.
- [ ] **Step 3: Implement.** `StickScanner` scans the root and `packages/` only.
  - It matches `*.zip` case-insensitively and skips any name starting with `.`, plus the `HKGPM` folder.
  - It sorts by `relPath`, ignoring case.
  - It looks up the entry named exactly `NEMESIS.data` with `ZipFile` (NEMESIS's `load_zip_meta` reads exactly that name, `pkg.c:235-239`) and reads at most 8192 bytes.
  - Without that entry: it gives the folder reason if any entry ends with `/NEMESIS.data`, and the no-meta reason otherwise.
  - When `[nemesis]` has `type` but no `id`, it gives the bundle reason.
  - `StatusLabeler` compares trimmed strings for equality only.
- [ ] **Step 4: Run the tests and confirm they pass.** Run `tools/test-java.sh`. Expected: `OK`.
- [ ] **Step 5: Commit** with the message "Find package zips on the stick and label them against installed packages".

### Task 4: Talking to NEMESIS safely

**Files:**
- Create: `core/src/hkgpackagemanager/core/{ShellQuote,CliResult,Nemesis,NemesisCalls,RestartHint}.java`, `core/test/hkgpackagemanager/core/FakeNemesis.java`
- Test: `core/test/hkgpackagemanager/core/{ShellQuoteTest,NemesisCallsTest,RestartHintTest}.java`

**Interfaces:**
- Produces:
  - `ShellQuote.quote(String s) -> String`: throws `IllegalArgumentException` on `\n`, `\r` or `\0`.
  - `CliResult`: public final `int exit; String output`; `String lastLine()` gives the last non-empty trimmed line, or null.
  - `interface Nemesis`:
    - `CliResult install(String absZipPath, boolean update)`
    - `CliResult uninstall(String id)`
    - `CliResult pkgList()`
    - `CliResult root(String shellCommand)`
  - `NemesisCalls`:
    - `install(String zip, boolean update) -> List<String>`: `["install","--update",zip]` or `["install",zip]`
    - `uninstall(String id)`, `pkgList()`, `root(String cmd)`: `["root","-c",cmd]`
    - `catCommand(String path) -> String`
    - `copyIntoCommand(String src, String dst) -> String`: `mkdir -p <dir> && cp <src> <dst>.tmp && mv <dst>.tmp <dst> && sync`, each path quoted
    - `fitsDaemon(List<String> argv) -> boolean`: every argument's UTF-8 length is at most 768
    - `installedMetaPath(String id) -> String`: `/data/nemesis/apps/<id>/NEMESIS.data`; requires `PackageId.isValid`
  - `RestartHint.mentionsRestart(String output) -> boolean`.
  - `FakeNemesis implements Nemesis` (test helper): it records calls, keeps an installed map `id -> version`, and takes a scripted install result per zip path (exit, output, or "kill" to simulate a reboot by throwing `FakeNemesis.Rebooted`).

- [ ] **Step 1: Write the failing tests.**

```java
@Test public void quotesSingleQuotesAndDollar() {
    assertEquals("'a b'", ShellQuote.quote("a b"));
    assertEquals("'it'\\''s $HOME'", ShellQuote.quote("it's $HOME"));
}
@Test(expected = IllegalArgumentException.class) public void refusesNewline() { ShellQuote.quote("a\nb"); }
@Test public void installPassesPathAsOneArgument() {
    assertEquals(Arrays.asList("install", "/storage/usb0/Enable ADB.zip"), NemesisCalls.install("/storage/usb0/Enable ADB.zip", false));
    assertEquals(Arrays.asList("install", "--update", "/x.zip"), NemesisCalls.install("/x.zip", true));
}
@Test public void copyCommand() {
    assertEquals("mkdir -p '/storage/usb0/HKGPM' && cp '/data/data/hkgpackagemanager.app/files/s/q' '/storage/usb0/HKGPM/queue.json.tmp'"
        + " && mv '/storage/usb0/HKGPM/queue.json.tmp' '/storage/usb0/HKGPM/queue.json' && sync",
        NemesisCalls.copyIntoCommand("/data/data/hkgpackagemanager.app/files/s/q", "/storage/usb0/HKGPM/queue.json"));
}
@Test public void daemonArgLimit() { assertTrue(NemesisCalls.fitsDaemon(list(repeat('a', 768)))); assertFalse(NemesisCalls.fitsDaemon(list(repeat('a', 769)))); }
@Test(expected = IllegalArgumentException.class) public void metaPathRejectsBadId() { NemesisCalls.installedMetaPath("../etc"); }
@Test public void restartHint() {
    assertTrue(RestartHint.mentionsRestart("reboot the head unit to finish"));
    assertTrue(RestartHint.mentionsRestart("Restart now"));
    assertFalse(RestartHint.mentionsRestart("removed hkg.x"));
}
@Test public void lastLine() { assertEquals("unzip failed", new CliResult(1, "a\nunzip failed\n\n").lastLine()); }
```

- [ ] **Step 2: Run the tests and confirm they fail.** Run `tools/test-java.sh`. Expected: compile errors.
- [ ] **Step 3: Implement.** `RestartHint` matches `(?i)\b(reboot|restart)`.
- [ ] **Step 4: Run the tests and confirm they pass.** Run `tools/test-java.sh`. Expected: `OK`.
- [ ] **Step 5: Commit** with the message "Add NEMESIS call building, shell quoting and restart hints".

### Task 5: The queue file

**Files:**
- Create: `core/src/hkgpackagemanager/core/{ItemState,QueueItem,Queue,QueueCodec,QueueOrder,Messages}.java`
- Test: `core/test/hkgpackagemanager/core/{QueueCodecTest,QueueOrderTest}.java`

**Interfaces:**
- Produces:
  - `enum ItemState { WAITING, INSTALLING, DONE, FAILED }`.
  - `QueueItem` is immutable:
    - public final `String relPath; long size; String sha256; String id; String version; boolean update; ItemState state; String message`
    - `withState(ItemState s, String message) -> QueueItem`
  - `Queue`: public final `boolean finished; List<QueueItem> items` (unmodifiable).
    - `withItem(int index, QueueItem item) -> Queue`
    - `finish() -> Queue`
    - `nextWaiting() -> int` (-1 if none)
    - `doneCount()`, `failedCount()`
  - `QueueCodec.encode(Queue q) -> byte[]` (UTF-8).
  - `QueueCodec.decode(byte[] data) -> Queue`: throws `QueueCodec.Unreadable(String why)`.
  - `QueueOrder.order(List<QueueItem> items) -> List<QueueItem>`: keeps the user's order; an item with `id` `hkg.package.manager` moves last.
  - `Messages`: the constants from Global Constraints (`MSG_INTERRUPTED`, `MSG_CHANGED`, `MSG_MISSING`, `MSG_UPDATE_GONE`, `MSG_DISCARDED`, `MSG_PATH_TOO_LONG`) and `installFailed(CliResult r)` / `versionMismatch(String got, String want)`.

Format: JSON *(owner, 2026-10-30)*, written with `org.json`. Android 4.4 has `org.json` built in, so nothing is bundled into the APK. Host tests use the Maven `org.json:json` jar that Task 2 adds to the toolchain.

```json
{
  "format": 1,
  "finished": false,
  "items": [
    {"relPath": "Enable ADB.zip", "size": 1234, "sha256": "…", "id": "adb.tcp",
     "version": "1.0", "update": false, "state": "WAITING", "message": ""}
  ]
}
```

`state` is the enum name. `encode` pretty-prints with an indent of 2, so the file is readable on a PC. Use only the `org.json` methods that exist in Android API 19:
- `JSONObject(String)`, `JSONArray`, `put`, `getString`, `getLong`, `getBoolean`, `getInt`, `getJSONArray`, `toString(int)`;
- no `keySet`, `similar`, `toList` or `optQuery`.

`tools/build-app.sh` compiles core against `android.jar`, which catches any method Android lacks.

- [ ] **Step 1: Write the failing tests.**

```java
@Test public void roundTrip() throws Exception {
    Queue q = new Queue(false, Arrays.asList(
        item("Enable ADB.zip", ItemState.DONE, ""),
        item("packages/x.zip", ItemState.FAILED, "install failed: a\tb\nc\\d")));
    assertEquals(q, QueueCodec.decode(QueueCodec.encode(q)));   // Queue and QueueItem implement equals
}
@Test public void rejectsTruncatedAndNewerFormat() {
    assertUnreadable("");                                                   // empty
    assertUnreadable("{\"format\":1,\"finished\":false,\"items\":[{\"relPath\":\"a.zip\""); // truncated JSON
    assertUnreadable("{\"format\":2,\"finished\":false,\"items\":[]}");     // newer format
    assertUnreadable("{\"format\":1,\"finished\":false,\"items\":[{\"relPath\":\"a.zip\"}]}"); // missing fields
    assertUnreadable(itemJson("\"state\":\"BOGUS\""));                      // unknown state
    assertUnreadable(itemJson("\"size\":\"big\""));                         // wrong type
}
@Test public void messageWithQuotesAndNewlinesSurvives() throws Exception // message "a \"b\"\nc\\d" round-trips
@Test public void selfUpdateLast() {
    List<QueueItem> o = QueueOrder.order(Arrays.asList(withId("hkg.package.manager"), withId("a"), withId("b")));
    assertEquals(Arrays.asList("a", "b", "hkg.package.manager"), ids(o));
}
```

- [ ] **Step 2: Run the tests and confirm they fail.** Run `tools/test-java.sh`. Expected: compile errors.
- [ ] **Step 3: Implement.** `decode` never throws anything but `Unreadable`.
- [ ] **Step 4: Run the tests and confirm they pass.** Run `tools/test-java.sh`. Expected: `OK`.
- [ ] **Step 5: Commit** with the message "Add the multi-install queue file format".

### Task 6: Stick bookkeeping and the resume script

**Files:**
- Create: `core/src/hkgpackagemanager/core/{StickFiles,StickLayout,DescriptorSwap,QueuePreflight}.java`, `core/test/hkgpackagemanager/core/MemStickFiles.java`, `stick/resume.sh`, `tests/resume/run.sh`, `tests/resume/stubs/{getprop,am,sleep}`
- Test: `core/test/hkgpackagemanager/core/{DescriptorSwapTest,QueuePreflightTest}.java`

**Interfaces:**
- Produces:
  - `interface StickFiles` (all paths relative to the stick root):
    - `boolean exists(String rel)`
    - `byte[] read(String rel) throws IOException`
    - `void writeAtomic(String rel, byte[] data) throws IOException`
    - `void move(String fromRel, String toRel) throws IOException`
    - `void delete(String rel) throws IOException`
  - `StickLayout` constants: `DESCRIPTOR = "NEMESIS.data"`, `RESUME = "HKGPM/resume.sh"`, `QUEUE = "HKGPM/queue.json"`, `SAVED = "HKGPM/NEMESIS.data.saved"`, `LOG = "HKGPM/log.txt"`.
  - `StickLayout.resumeDescriptor() -> byte[]`, exactly `[nemesis]\nversion=1.0\ntype=execute\npath=HKGPM/resume.sh\n`.
  - `DescriptorSwap.begin(StickFiles s, byte[] resumeScript) throws IOException` and `DescriptorSwap.end(StickFiles s) throws IOException`.
  - `DescriptorSwap.isOurs(byte[] descriptor) -> boolean`: true when the INI's `[nemesis] path` equals `HKGPM/resume.sh`.
  - `QueuePreflight.otherDescriptorRoot(List<String> mountedRoots, String chosenRoot, QueuePreflight.Exists descriptorExists) -> String`: the first other root that has `NEMESIS.data`, or null.
  - `interface QueuePreflight.Exists { boolean at(String root); }`. Core ships inside the APK, and API 19 has no `java.util.function`. Lambdas are fine, because d8 desugars them for `--min-api 19`.
  - `MemStickFiles implements StickFiles` (test helper): a map-backed store; can be told to fail every write after N writes (`failWritesAfter(int n)`).

- [ ] **Step 1: Write the failing tests.**

```java
@Test public void beginSavesForeignDescriptorAndWritesOurs() throws Exception {
    MemStickFiles s = new MemStickFiles(); s.put("NEMESIS.data", "[nemesis]\ntype=install\npath=packages/a.zip\n");
    DescriptorSwap.begin(s, bytes("#!/system/bin/sh\n"));
    assertEquals("[nemesis]\ntype=install\npath=packages/a.zip\n", s.text("HKGPM/NEMESIS.data.saved"));
    assertArrayEquals(StickLayout.resumeDescriptor(), s.read("NEMESIS.data"));
    assertEquals("#!/system/bin/sh\n", s.text("HKGPM/resume.sh"));
}
@Test public void beginTwiceKeepsOriginalSaved()   // second begin must not overwrite SAVED with our own descriptor
@Test public void endRestoresOriginal()            // after end: NEMESIS.data == original text, SAVED gone
@Test public void endWithoutOriginalRemovesOurs()  // no original: after end NEMESIS.data absent
@Test public void endLeavesForeignDescriptorAlone()// NEMESIS.data replaced by user with a foreign one mid-queue: end does not delete it
@Test public void preflight() {
    assertEquals("/storage/usb1", QueuePreflight.otherDescriptorRoot(roots("/storage/usb0", "/storage/usb1"), "/storage/usb0", r -> r.endsWith("1")));
    assertNull(QueuePreflight.otherDescriptorRoot(roots("/storage/usb0"), "/storage/usb0", r -> true));
}
```

`tests/resume/run.sh` (runs `stick/resume.sh` under `$MOCK_SH` with stubs):
- with `sys.boot_completed` = `1`, `am` is called exactly once, with `start -n hkgpackagemanager.app/.MainActivity --ez resume true`, and the exit is 0;
- with the `getprop` stub returning `0` three times and then `1`, `sleep` is called three times before `am`;
- with it returning `0` forever, the script gives up after its limit (300 s with 2 s sleeps = 150 sleeps), exits 0 and never calls `am`;
- `tools/lint-sh.sh stick` passes.

- [ ] **Step 2: Run the tests and confirm they fail.** Run `tools/test-java.sh; sh tests/resume/run.sh`. Expected: compile errors and FAIL lines.
- [ ] **Step 3: Implement the Java classes and `stick/resume.sh`.** The script:
  - starts with `#!/system/bin/sh` and the PATH line;
  - waits while `getprop sys.boot_completed` isn't `1`, with `sleep 2`, up to 150 times;
  - then runs `am start -n hkgpackagemanager.app/.MainActivity --ez resume true`;
  - always exits 0 (a non-zero exit makes NEMESIS show a failure dialog).
- [ ] **Step 4: Run the tests and confirm they pass.** Run `tools/test-java.sh && sh tests/resume/run.sh`. Expected: `OK` and all `ok`.
- [ ] **Step 5: Commit** with the message "Add resume descriptor handling and the resume script".

### Task 7: Running the queue

**Files:**
- Create: `core/src/hkgpackagemanager/core/{ZipFingerprint,ZipCheck,QueueStore,InstallStep,QueueRunner,QueueSession}.java`
- Test: `core/test/hkgpackagemanager/core/{InstallStepTest,QueueRunnerTest,QueueSessionTest}.java`

**Interfaces:**
- Consumes:
  - from Task 3: `PkgList`;
  - from Task 4: `Nemesis`, `NemesisCalls`, `CliResult`, `FakeNemesis`;
  - from Task 5: `Queue`, `QueueItem`, `ItemState`, `QueueCodec`, `QueueOrder`, `Messages`;
  - from Task 6: `StickFiles`, `StickLayout`, `DescriptorSwap`, `MemStickFiles`.
- Produces:
  - `ZipFingerprint`: public final `long size; String sha256`.
  - `interface ZipCheck { ZipFingerprint fingerprint(String relPath) throws IOException; }`: returns null when the file is missing.
  - `interface QueueStore { void save(Queue q) throws IOException; }`
  - `InstallStep`:
    - `InstallStep(Nemesis n, String stickRoot)`
    - `QueueItem perform(QueueItem item)`: returns a DONE or FAILED item
    - `String installedVersion(String id)`: via `root(catCommand(installedMetaPath(id)))`, then `IniFile`; null if unreadable
    - `Set<String> installedIds()`
  - `QueueRunner`:
    - `QueueRunner(InstallStep step, QueueStore store, ZipCheck check, Listener l)`
    - `interface Listener { void changed(Queue q); }`
    - `Outcome run(Queue q)`
    - `Queue resolveInterrupted(Queue q)`
    - `static final class Outcome { public final Queue queue; public final boolean stopped; public final String stopReason; }`
  - `QueueSession`:
    - `QueueSession(StickFiles stick, String stickRoot, Nemesis n, ZipCheck check, byte[] resumeScript, QueueRunner.Listener l)`
    - `QueueRunner.Outcome start(List<QueueItem> selected) throws IOException`
    - `QueueRunner.Outcome resume() throws IOException`
    - `Queue loadUnfinished() throws QueueCodec.Unreadable`: null when there's no queue file or it's finished
    - `void discard() throws IOException`

Behaviour (from the spec, "Multi-package install"):

- **`perform`:** if `!NemesisCalls.fitsDaemon(install argv)`, FAILED with `MSG_PATH_TOO_LONG`. Otherwise run `install(stickRoot + "/" + relPath, item.update)`.
  - **Success** = exit 0, and the id is in `pkgList()`, and `installedVersion(id)` equals `item.version` after trimming. That's DONE with an empty message.
  - **Exit 0 but the version differs:** FAILED with `versionMismatch`.
  - **Non-zero exit:** if `item.update` and the id is no longer listed, FAILED with `MSG_UPDATE_GONE`. Otherwise FAILED with `installFailed(result)`.
- **`run`:** for each waiting item, in order:
  1. Take its fingerprint. If it's missing, FAILED with `MSG_MISSING`. If the size or sha differs, FAILED with `MSG_CHANGED`.
  2. Save `INSTALLING`.
  3. Call `perform`.
  4. Save the result.

  After the last item, the queue is marked finished and saved. **Any `save` failure stops the run at once** (`stopped=true`, `stopReason` = the exception message) and no further install starts. The listener is called after every state change.
- **`resolveInterrupted`:** each `INSTALLING` item becomes DONE if the id is listed and the version matches. Otherwise it becomes FAILED with `MSG_INTERRUPTED`. Items are never retried.
- **`start`:**
  1. Refuse with `IllegalStateException` when `loadUnfinished()` is non-null.
  2. Order with `QueueOrder`.
  3. Fill each item's size and sha from `ZipCheck`.
  4. `DescriptorSwap.begin`, then save the queue, then `run`.
  5. If not stopped: `DescriptorSwap.end`.
- **`resume`:** `loadUnfinished()`, then `resolveInterrupted`, then save, then `run`, then `end` (if not stopped).
- **`discard`:** waiting and installing items become FAILED with `MSG_DISCARDED`; the queue is finished and saved; then `end`.
- `QueueSession` saves through `StickFiles.writeAtomic(StickLayout.QUEUE, QueueCodec.encode(q))`.

- [ ] **Step 1: Write the failing tests.** Use `FakeNemesis`, `MemStickFiles` and a map-backed `ZipCheck`.

```java
@Test public void threeItemsMiddleFailsOthersInstall()      // results DONE, FAILED("install failed: unzip failed"), DONE; finished; summary counts 2/1
@Test public void rebootMidQueueThenResume()                // FakeNemesis "kill" on item 2 after marking it installed (version matches) -> exception escapes run;
                                                            // new session.resume(): item 2 DONE, item 3 installed, NEMESIS.data restored/removed
@Test public void interruptedNotInstalledIsFailedNotRetried() // item left INSTALLING, id absent -> FAILED MSG_INTERRUPTED; FakeNemesis saw no second install of it
@Test public void changedZipIsNotInstalled()                // fingerprint sha differs -> FAILED MSG_CHANGED, no install call
@Test public void missingZip()                              // fingerprint null -> FAILED MSG_MISSING
@Test public void saveFailureStopsBeforeNextInstall()       // MemStickFiles.failWritesAfter(k) -> outcome.stopped, FakeNemesis install count == items started before failure
@Test public void updateThatRemovedPackage()                // update=true, exit 1, id gone from pkg-list -> FAILED MSG_UPDATE_GONE
@Test public void versionMismatch()                         // exit 0, installed "1.0" want "1.1" -> FAILED "installed version is 1.0, expected 1.1"
@Test public void pathTooLong()                             // stickRoot+relPath > 768 bytes -> FAILED MSG_PATH_TOO_LONG, no install call
@Test public void selfUpdateRunsLast()                      // install call order ends with hkg.package.manager
@Test public void startRefusesWhenUnfinishedQueueExists()
@Test public void discardMarksRemainingAndRestoresDescriptor()
@Test public void loadUnfinishedReturnsNullForFinishedQueue()
```

- [ ] **Step 2: Run the tests and confirm they fail.** Run `tools/test-java.sh`. Expected: compile errors.
- [ ] **Step 3: Implement the six types.**
- [ ] **Step 4: Run the tests and confirm they pass.** Run `tools/test-java.sh`. Expected: `OK`.
- [ ] **Step 5: Commit** with the message "Run the multi-install queue and resume it after restarts".

### Task 8: Button press counting and settings

Needs `docs/notes/key-capture.md` from Task 1, Step 8.

**Files:**
- Create: `core/src/hkgpackagemanager/core/{Button,Settings,SettingsCodec,PressCounter}.java`
- Test: `core/test/hkgpackagemanager/core/{SettingsCodecTest,PressCounterTest}.java`

**Interfaces:**
- Produces:
  - `enum Button { OFF(null), NAVIGATION("com.hkmc.intent.action.NAVI_SHORTCUT"), MEDIA(<action from key-capture.md>) }`, with `public final String action` and `static Button forAction(String action)` (null if no match).
  - `Settings`: public final `Button button; int presses`. `Settings.DEFAULT` is `(MEDIA, 3)`, or `(NAVIGATION, 3)` if key-capture.md shows Media sends no shortcut broadcast; in that case also drop `MEDIA` from the enum.
  - `SettingsCodec.decode(String text) -> Settings`: never throws; unknown values fall back to the default and presses are clamped to 2..5.
  - `SettingsCodec.encode(Settings s) -> String`: `button=media\npresses=3\n`.
  - `PressCounter(int needed)`, with `boolean press(long nowMs)`: true when this press completes `needed` presses.
    - A gap over 1500 ms restarts the count at 1.
    - A press within 200 ms of the previous counted press is ignored.
    - After triggering, the count resets.
  - `static boolean conflictsWithNemesisManager(Button b)`: true for NAVIGATION (NEMESIS Manager uses Navigation or Setup).

- [ ] **Step 1: Write the failing tests.**

```java
@Test public void threePressesWithinWindow() { PressCounter c = new PressCounter(3);
    assertFalse(c.press(0)); assertFalse(c.press(500)); assertTrue(c.press(1000)); assertFalse(c.press(1400)); }
@Test public void slowPressRestarts() { PressCounter c = new PressCounter(3);
    c.press(0); c.press(500); assertFalse(c.press(2100)); assertFalse(c.press(2600)); assertTrue(c.press(3000)); }
@Test public void repeatWithin200msIgnored() { PressCounter c = new PressCounter(2);
    assertFalse(c.press(0)); assertFalse(c.press(150)); assertTrue(c.press(400)); }
@Test public void codec() {
    assertEquals(new Settings(Button.NAVIGATION, 5), SettingsCodec.decode("button=navigation\npresses=9\n"));
    assertEquals(Settings.DEFAULT, SettingsCodec.decode("garbage"));
    assertEquals("button=off\npresses=2\n", SettingsCodec.encode(new Settings(Button.OFF, 2)));
}
@Test public void mediaActionFromKeyCapture() { assertEquals("<exact string from key-capture.md>", Button.MEDIA.action); }
```

- [ ] **Step 2: Run the tests and confirm they fail.** Run `tools/test-java.sh`. Expected: compile errors.
- [ ] **Step 3: Implement**, putting Media's action string from `docs/notes/key-capture.md` in both the enum and the test.
- [ ] **Step 4: Run the tests and confirm they pass.** Run `tools/test-java.sh`. Expected: `OK`.
- [ ] **Step 5: Commit** with the message "Add button press counting and settings".

### Task 9: Android plumbing: CLI runner, stick file system, state, log

**Files:**
- Create: `app/AndroidManifest.xml` (application only; components come in Task 10), `app/src/hkgpackagemanager/app/{NemesisCli,StickFs,Sticks,AppLog,StateStore}.java`, `tools/build-app.sh`, `tests/app/check-app.sh`

**Interfaces:**
- Consumes:
  - from Task 4: `Nemesis`, `NemesisCalls`, `CliResult`;
  - from Task 6: `StickFiles`, `StickLayout`;
  - from Task 7: `ZipCheck`, `ZipFingerprint`;
  - from Task 8: `Settings`, `SettingsCodec`.
- Produces:
  - `NemesisCli implements Nemesis`: `ProcessBuilder` on `/system/xbin/nemesis` plus the `NemesisCalls` argv, with stderr merged. It returns `CliResult(exitCode, output)`. Every call and its result goes to `AppLog`.
  - `StickFs implements StickFiles, ZipCheck`:
    - constructed with `StickFs(Context c, String stickRoot, Nemesis n)`;
    - `writeAtomic` tries a direct write (`rel.tmp`, `FileOutputStream.getFD().sync()`, `renameTo`). On `IOException` or `SecurityException` it writes to `filesDir/stage/<name>` and runs `n.root(NemesisCalls.copyIntoCommand(...))`; a non-zero exit throws `IOException(lastLine)`;
    - `move` and `delete` work the same way, with `mv` and `rm -f` fallbacks built with `ShellQuote`;
    - `fingerprint` streams SHA-256 with a 64 KiB buffer;
    - `boolean lastWriteUsedRoot()` is exposed for the on-car checklist.
  - `Sticks.mounted() -> List<String>`: the `/storage/usb0..2` paths that are directories with a non-null `list()`.
  - `AppLog.get(Context)`: `log(String line)` (timestamped, `filesDir/log.txt`, rotated to `log.1.txt` past 256 KiB) and `byte[] snapshot()`.
  - `StateStore(Context, Nemesis)`: `Settings load()` reads `/data/nemesis/hkg.package.manager/settings.txt` via `root(catCommand)`, refreshes the cache at `filesDir/settings.txt`, and falls back to the cache, then `Settings.DEFAULT`. `void save(Settings)` writes the cache, then root-copies it. `Settings cached()` reads the cache only (for the receiver).

- [ ] **Step 1: Write the failing check** `tests/app/check-app.sh`. It runs `tools/build-app.sh`, then checks:
  - `aapt dump badging` shows `package: name='hkgpackagemanager.app'` and `sdkVersion:'19'`;
  - the APK contains `classes.dex` and `assets/resume.sh`;
  - `assets/resume.sh` is byte-identical to `stick/resume.sh`;
  - `apksigner verify` passes.

  Run `sh tests/app/check-app.sh`. Expected: FAIL (no build script).
- [ ] **Step 2: Write `tools/build-app.sh`** from `../hkg-climate-page/tools/build-app.sh`, with these changes:
  - compile `core/src` and `app/src` (no stubs);
  - copy `stick/resume.sh` to `build/app/assets/resume.sh` and package it with `aapt package ... -A build/app/assets`;
  - output `build/app/hkgpm.apk`;
  - key `private/keys/hkgpm.jks`, alias `hkgpm`, `-dname "CN=hkg-package-manager"`;
  - `VERSION_NAME` default `1.0.0`, `VERSION_CODE` default `1`.

  Write the five Java classes.
- [ ] **Step 3: Run the check and confirm it passes.** Run `tools/test-java.sh && sh tests/app/check-app.sh`. Expected: `OK` and all `ok`.
- [ ] **Step 4: Commit** with the message "Add the Android CLI runner, stick file system, state store and log".

### Task 10: Queue service and receivers

**Files:**
- Modify: `app/AndroidManifest.xml`
- Create: `app/src/hkgpackagemanager/app/{QueueService,ButtonReceiver,MountReceiver}.java`
- Modify: `tests/app/check-app.sh`

**Interfaces:**
- Consumes:
  - from Task 3: `StickScanner`;
  - from Task 7: `QueueSession`, `QueueRunner.Outcome`;
  - from Task 8: `PressCounter`, `Button`;
  - from Task 9: `NemesisCli`, `StickFs`, `Sticks`, `StateStore`, `AppLog`.
- Produces:
  - **Intent actions** (constants in `QueueService`):
    - `ACTION_START = "hkgpackagemanager.app.action.START"`, with extras `EXTRA_ROOT` (String), `EXTRA_PATHS` (String[] relPaths) and `EXTRA_UPDATES` (boolean[]);
    - `ACTION_RESUME = "hkgpackagemanager.app.action.RESUME"`, with `EXTRA_ROOT`;
    - `ACTION_DISCARD = "hkgpackagemanager.app.action.DISCARD"`, with `EXTRA_ROOT`;
    - `ACTION_POSTINSTALL = "hkgpackagemanager.app.action.POSTINSTALL"`.
  - **Service status:**
    - `static boolean isRunning()`
    - `static Queue current()`: the last queue the listener saw
    - `static QueueRunner.Outcome lastOutcome()`
    - a broadcast `hkgpackagemanager.app.action.CHANGED` (package-local) on every listener call.
  - `MainActivity` extras used here: `EXTRA_RESUME = "resume"` (boolean) and `EXTRA_ROOT`.

Behaviour:
- `QueueService` runs one worker thread and `startForeground` with a plain notification (API 19 `Notification.Builder`). Start, resume and discard requests made while `isRunning()` are logged and ignored, which covers the spec's "handler re-arm" side effect. `isRunning` is a static `AtomicBoolean` set before the thread starts.
- When the run ends (finished or stopped), the service stops itself and starts `MainActivity` on the Progress screen (`FLAG_ACTIVITY_NEW_TASK`).
- `ACTION_POSTINSTALL`: for each mounted stick, if `loadUnfinished()` is non-null, resume it. Otherwise do nothing and stop. This first start also takes the app out of Android's "stopped" state, so the receivers work.
- `ButtonReceiver` is registered in the manifest for the `Button.NAVIGATION` and `Button.MEDIA` action strings. It reads `StateStore.cached()` and ignores actions other than the chosen button. It holds a static `PressCounter`, recreated when the press count changes. When it triggers, it starts `MainActivity`.
- `MountReceiver` is registered in the manifest for `android.intent.action.MEDIA_MOUNTED` with `<data android:scheme="file"/>`. If the mounted path is a stick root with an unfinished queue and the service isn't running, it starts `MainActivity` with `EXTRA_ROOT` (which prompts).

- [ ] **Step 1: Extend the failing check** `tests/app/check-app.sh`. Using `aapt dump xmltree build/app/hkgpm.apk AndroidManifest.xml`, check that:
  - the manifest declares `hkgpackagemanager.app.QueueService` and `hkgpackagemanager.app.MainActivity` (with `MAIN`/`LAUNCHER`);
  - the `ButtonReceiver` has both button action strings;
  - the `MountReceiver` has `MEDIA_MOUNTED` with scheme `file`.

  Run `sh tests/app/check-app.sh`. Expected: FAIL.
- [ ] **Step 2: Implement** the manifest components and the three classes. `MainActivity` gets a stub here (an empty activity), filled in by Task 11.
- [ ] **Step 3: Run the checks and confirm they pass.** Run `tools/test-java.sh && sh tests/app/check-app.sh`. Expected: `OK` and all `ok`.
- [ ] **Step 4: Commit** with the message "Add the queue service and the button and mount receivers".

### Task 11: Screens

**Files:**
- Create: `app/src/hkgpackagemanager/app/{MainActivity,DriveScreen,InstalledScreen,ProgressScreen,SettingsScreen}.java` (replacing the Task 10 stub)

**Interfaces:**
- Consumes:
  - from Task 3: `StickScanner`, `StatusLabeler`, `PkgList`;
  - from Task 4: `RestartHint`, `NemesisCalls`;
  - from Task 6: `QueuePreflight`;
  - from Task 7: `InstallStep`, `QueueSession`;
  - from Task 8: `Settings`, `Button`, `PressCounter.conflictsWithNemesisManager`;
  - from Task 9: `StateStore`, `AppLog`, `StickFs`;
  - from Task 10: `QueueService` actions and status.

Contents are fixed by the spec ("Screens"); layout and style are the builder's. Views are built in code, as in `../hkg-climate-page/app/src`. The tab bar is a horizontal row of four buttons over a content frame. Text is at least 24sp, and tap targets at least 64dp. All CLI work runs off the UI thread (an `AsyncTask` or a plain `Thread` plus a `Handler`).

- **`MainActivity`:**
  - On create and on new intent: if `QueueService.isRunning()`, show Progress.
  - Otherwise, for each mounted stick (`EXTRA_ROOT` first), `loadUnfinished()`:
    - unreadable: a dialog with the reason and Discard / Keep;
    - unfinished and `EXTRA_RESUME` true: send `ACTION_RESUME`;
    - unfinished otherwise: a dialog `Unfinished install found: continue?` listing items and states, with Continue (`ACTION_RESUME`) / Discard (`ACTION_DISCARD`).
  - Otherwise show the USB drive tab.
  - It listens for `CHANGED` while visible.
- **`DriveScreen`:** a stick picker when more than one stick is mounted, and a row per `StickZip`: name, id, version, status text.
  - Status texts: `New` / `Update (installed X, drive Y)` / `Same version`, or the problem greyed out.
  - Rows have a checkbox and an Install/Update button. The bar has Select all and Install selected.
  - **Single install:** a confirm dialog, then `InstallStep.perform` on a thread, then a result dialog (`Installed`, or the FAILED message).
  - **Install selected:** first `QueuePreflight.otherDescriptorRoot`. If it's non-null, show `Remove the other USB drive (<root>) first: NEMESIS only reads one drive.` Then a confirm dialog listing the packages, then `ACTION_START`.
- **`InstalledScreen`:** `pkgList()` plus `installedVersion(id)` per package. Each row shows name, id, version (or `unknown`), types and `locked`. Uninstall appears only when `!locked && !id.equals("hkg.package.manager")`. Uninstall asks for confirmation, runs `uninstall(id)`, and shows the output, adding `Restart the head unit to finish.` when `RestartHint.mentionsRestart(output)`.
- **`ProgressScreen`:** a line per item (name, state, message) and the handler note, verbatim. When finished: `Finished: <done> installed, <failed> failed`, followed by the failed items and their messages. When stopped: `Stopped: <stopReason>. Plug the drive back in to continue.`
- **`SettingsScreen`:** a button choice from the `Button` values (labels `Off`, `Navigation`, `Media`) and presses 2-5, saved with `StateStore.save`. When `conflictsWithNemesisManager` is true, show `NEMESIS Manager may also open on this button.` A Copy log to USB drive button writes `AppLog.snapshot()` to `StickLayout.LOG`.

- [ ] **Step 1: Implement** the five classes.
- [ ] **Step 2: Build and check.** Run `tools/test-java.sh && sh tests/app/check-app.sh`. Expected: `OK` and all `ok`.
- [ ] **Step 3: Optional emulator smoke.** If an API 19 emulator image is available: `adb install build/app/hkgpm.apk && adb shell am start -n hkgpackagemanager.app/.MainActivity`. Expected: four tabs render; Installed shows an error line (no NEMESIS on the emulator) and doesn't crash.
- [ ] **Step 4: Commit** with the message "Add the drive, installed, progress and settings screens".

### Task 12: NEMESIS package and USB install stick

**Files:**
- Create: `package/NEMESIS.data`, `package/scripts/{common,pre,post,preun}.sh`, `tools/build-package.sh`, `tests/package/run.sh`, `tests/package/stubs/{nemesis,am,getprop}`

**Interfaces:**
- Consumes: `build/app/hkgpm.apk` (Task 9).
- Produces:
  - `dist/hkg-package-manager.zip`: the outer `NEMESIS.data` (`type=install`, `path=packages/hkg.package.manager.zip`) plus the inner package zip.

`package/NEMESIS.data`:

```ini
[nemesis]
id=hkg.package.manager
name=HKG Package Manager
version=1.0.0
types=application
preinstall=scripts/pre.sh
postinstall=scripts/post.sh
preuninstall=scripts/preun.sh

[app.0]
kind=apk
path=app/hkgpm.apk
package=hkgpackagemanager.app
component=hkgpackagemanager.app/.MainActivity
```

The hooks run as root with `cwd` set (staging for pre, the package dir for post and preun), and log to `/data/nemesis/hkg.package.manager/install.log`.

- **`pre.sh`:** fail (exit 1, with a toast `HKG Package Manager needs NEMESIS with its command channel running.`) unless `/system/xbin/nemesis` is executable and `nemesis root 10999 -c '/system/xbin/nemesis pkg-list'` exits 0. That proves an ordinary app uid reaches the daemon.
- **`post.sh`:** `mkdir -p /data/nemesis/hkg.package.manager` (mode 755), then `am startservice -n hkgpackagemanager.app/.QueueService -a hkgpackagemanager.app.action.POSTINSTALL`. A failure is logged as WARN and the script still exits 0.
- **`preun.sh`:** `am force-stop hkgpackagemanager.app`. It does **not** remove `/data/nemesis/hkg.package.manager` (NEMESIS runs this hook on updates too, `pkg.c` `have_existing && update` block). Exit 0.

- [ ] **Step 1: Write the failing test** `tests/package/run.sh`. It runs each hook under `$MOCK_SH` with stubs and a fake `/data` root (`PM_SYSROOT`, honoured by `common.sh` as `KIT_SYSROOT` is in the climate kit). Checks:
  - `pre.sh` exits 1 when the `nemesis` stub fails `root`, and the `am` log has the toast;
  - `pre.sh` exits 0 when the stub succeeds;
  - `post.sh` creates the state dir and calls `am startservice` with `hkgpackagemanager.app.action.POSTINSTALL`;
  - `preun.sh` leaves an existing `settings.txt` in place;
  - `tools/lint-sh.sh package` passes;
  - the built zip's inner package contains `NEMESIS.data`, `scripts/*.sh` and `app/hkgpm.apk`, and its outer `NEMESIS.data` has `path=packages/hkg.package.manager.zip`.

  Run `sh tests/package/run.sh`. Expected: FAIL.
- [ ] **Step 2: Implement** the hooks, `common.sh` (`log`, `toast` as in `../hkg-climate-page/package/scripts/common.sh`, `STATE_DIR`) and `tools/build-package.sh`. Base it on `../hkg-climate-page/tools/build-package.sh` with fixed modes and timestamps; call `tools/lint-sh.sh package stick` instead of the inline lint; no patch, lpatch or BUILD changes beyond writing `BUILD`.
- [ ] **Step 3: Run the tests and confirm they pass.** Run `tools/build-app.sh && sh tests/package/run.sh`. Expected: all `ok`, and `dist/hkg-package-manager.zip` is printed.
- [ ] **Step 4: Commit** with the message "Package the manager for NEMESIS".

### Task 13: On-car checklist and README

**Files:**
- Create: `docs/testing/2026-10-30-phase-1.md`
- Modify: `README.md`

- [ ] **Step 1: Write the checklist** in the style of `../hkg-climate-page/docs/testing/`. Numbered steps with an expected result each, covering the spec's Phase 1 list:
  1. Install the manager from `dist/hkg-package-manager.zip`. Then press Media three times: the manager opens. Press Navigation three times: NEMESIS Manager opens, and the manager doesn't.
  2. Prepare a stick with three test packages (build them with `../hmg_nemesis/Sources/tools/make_nemesis_package.py`):
     - `test.a` writes a marker file;
     - `test.b` runs `exec /system/bin/reboot` in its `postinstall`;
     - `test.c` writes a marker file.

     Plus `test.fail`, whose `preinstall` exits 1, and `Enable ADB.zip`-style names with spaces.
  3. **Single install** of `test.a`: result `Installed`, and the Installed tab shows its version.
  4. **Queue** a, b, c:
     - NEMESIS asks about the USB item at the start; either answer is fine.
     - The head unit restarts during b.
     - After the restart, answer Yes: the queue continues.
     - Final report: 3 installed.
     - The stick's `NEMESIS.data` is restored or removed.
  5. **Queue with `test.fail` in the middle:** it's skipped, and the report lists its reason.
  6. **Pull the stick mid-queue:** the Progress screen shows the stopped message. Reinsert it: the prompt appears, and Continue resolves the interrupted item.
  7. **Self-update** (bump `VERSION_NAME`) in a queue with `test.c`: the manager's update runs last, and the report appears after it restarts.
  8. **Uninstall** `test.a`, then hkg-climate-page (restart hint shown). The locked `usb-handler` shows no Uninstall.
  9. **Settings:** change to Navigation (conflict note), restart, check that the setting survived. Self-update again: the setting survived.
  10. **Copy log to USB.** Note whether stick writes used the root fallback (log line from `StickFs`).
- [ ] **Step 2: Update the README.** Status line, build steps (`tools/toolchain.sh fetch`, `tools/test-java.sh`, `tools/build-app.sh`, `tools/build-package.sh`, `tools/build-kit.sh`), and how to install.
- [ ] **Step 3: Run the whole suite.** Run `tools/test-java.sh && sh tests/kit/run.sh && sh tests/resume/run.sh && sh tests/app/check-app.sh && sh tests/package/run.sh`. Expected: all pass.
- [ ] **Step 4: Commit** with the message "Add the Phase 1 on-car checklist and build instructions".
