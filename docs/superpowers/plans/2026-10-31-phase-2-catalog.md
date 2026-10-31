# Phase 2: Catalog Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The manager reads signed package catalogs from a USB stick, shows each package's
compatibility in three colours and gates installs by colour; a desktop CLI mirrors a catalog
onto a stick.

**Architecture:** A catalog is a folder (`index.json`, `index.json.sig`, `key.pem`, zips).
Pure Java in `core/` parses and verifies it, matches compatibility rules against facts about
the car, and feeds catalog zips into the Phase 1 queue with their expected SHA-256. The
Android classes in `app/` only probe the car, store trusted key fingerprints through the
NEMESIS root channel and draw the screens. A stdlib-only Python CLI does the mirroring.
A test-only Python builder makes the sample and on-car catalogs.

**Tech Stack:** Java 8 source against `android-19` (org.json from android.jar), JUnit 4 on
the host, Python 3.8+ standard library, `openssl` (test builder only), GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-10-30-hkg-package-manager-design.md`, section
"Phase 2: catalog format, catalog on a stick, mirror CLI" (plus "Constraints" and "Where
the manager keeps its state").

## Global Constraints

- Plain Java against `android-19`, no third-party libraries. Core code must compile against
  android.jar: no `java.util.Base64`, no `java.util.function`, no `java.nio.file`. Match the
  existing idiom: anonymous classes, explicit `close()` in `finally`, no try-with-resources.
- "Phase 1 must not need Phase 2's catalog." A stick with only loose zips works as before;
  the only change for it is the yellow warning in the confirmations.
- "A catalog format tied to one host (GitHub-only URLs, an API server, accounts)" is an anti-goal.
- "A stick-derived string (file name, package field) reaching a root shell unquoted" is an
  anti-goal: every root command quotes through `ShellQuote.quote`.
- "A red (known incompatible) package installable with a single tap" is an anti-goal.
- Trusted keys live in `/data/nemesis/hkg.package.manager/` (survives updates and
  uninstalls), written and read through the root channel, cached in the app's files.
- "No developer tooling before Phase 3": the catalog builder lives under `tests/` and is
  for tests and the on-car checklist only. The format doc tells publishers how to sign
  with `openssl`.
- Python: standard library only, 3.8+. Kit and package shell rules (mksh/toolbox) still apply.
- No per-car secrets, no manufacturer files, no car captures in the repo.
- Commits: plain words, ending with the session's attribution lines.

## Decisions this plan makes (the spec left them to the builder)

| Item | Decision |
|---|---|
| Signature | RSA PKCS#1 v1.5 with SHA-256 (`SHA256withRSA`) over the exact bytes of `index.json`; keys of 2048 bits or more. API 19 verifies it with the platform's own crypto, and the CLI verifies it with stdlib `pow()`: no crypto package needed. |
| Signature file | `index.json.sig`, the raw signature bytes (what `openssl dgst -sha256 -sign` writes). |
| Key | `key.pem` in the catalog folder: an SPKI `PUBLIC KEY` PEM. The **fingerprint** (= key id) is the lowercase hex SHA-256 of the SPKI DER. The manager stores only trusted fingerprints, so a catalog is trusted when its `key.pem` hashes to a trusted fingerprint and verifies the signature. |
| Field names | Index: `format`, `id`, `name`, `publisher`, `generated`, `key`, `packages`. Entry: `id`, `name`, `version`, `description`, `author`, `homepage` (optional), `zip`, `size`, `sha256`, `compatibility` (optional) holding `works_on`, `not_on` (lists of rules) and `conflicts` (list of ids). Rule: `build` (glob), `hardware` (list of names). |
| Format version | `format` is an integer; this manager reads `1` and refuses anything higher with "This catalog needs a newer HKG Package Manager (format N)." |
| Zip location | A relative `zip` is under the catalog folder (no leading `/`, no `\`, no empty, `.` or `..` segment, must not start with `remote/`). An `http://` or `https://` URL is stored on the stick at `remote/<id>.zip`. Any other scheme makes that entry invalid. |
| Strictness | Unknown fields are ignored. A known field with the wrong type makes its entry invalid (shown with the reason, not installable), so a damaged `not_on` can never turn red into yellow. |
| Hash at listing | The drive screen checks size and SHA-256 when listing (cached by path, size and mtime) and again just before each install. |
| Folder | A catalog's folder name on the stick must equal its `id`. |
| Wi-Fi | `true` when `/sys/class/net/wlan*` exists; `false` when the kernel log has `failed to power up DHD generic adapter, max retry reached`; otherwise unknown. A rule naming `wifi` or `!wifi` never matches while it's unknown. |
| Build id | `getprop ro.build.product`. Unknown (empty) means no `build` condition matches. |
| Mirror CLI trust | The CLI keeps fingerprints in `~/.config/hkg-package-manager/trusted-keys.txt` (same format as the car's `keys.txt`). An unknown key stops the run, printing the fingerprint; `--fingerprint <hex>` trusts and stores it. |
| Version | Phase 2 ships as **1.1.0**, versionCode **4** (the car has run test builds up to versionCode 3). |

## Review Focus

1. **A `zip` location that escapes the catalog folder** (`../x.zip`, `/system/x.zip`,
   `a/../../x.zip`, `a\b.zip`): the entry is invalid; nothing outside
   `HKGPM/catalogs/<id>/` is ever read or installed. Test: Task 4,
   `zipLocationThatEscapesIsInvalid`.
2. **A damaged or hostile index**: not JSON, a UTF-8 BOM, a 5 MB file, `packages` not a list,
   `size` as a string. The catalog shows an error row or the entry shows its reason; the
   screen never crashes. Tests: Task 4 `bomIsAccepted`, `wrongTypesInvalidateOnlyThatEntry`;
   Task 5 `oversizedIndexIsBroken`.
3. **A messy `HKGPM/catalogs/`**: a stray file, a folder missing `index.json.sig` or
   `key.pem`, a folder whose name isn't the catalog id. Each gives one broken row with a
   reason; the others still list. Test: Task 5, `messyCatalogFolderGivesOneBrokenRowEach`.
4. **A zip swapped after listing, in a single install** (not a queue): refused with the
   mismatch message. Test: Task 6 `zipVerifyCatchesSwap`; Task 9 wires `ZipVerify` into
   the single install.
5. **A mirror run interrupted mid-download, then re-run**: no half-written zip ever sits
   under its final name, and the re-run completes the stick. Test: Task 11,
   `interruptedDownloadLeavesNoFinalFile`.

---

## File Structure

```
docs/catalog/format.md                 the open format spec (publishers read this)
docs/catalog/catalog.schema.json       JSON Schema (draft 2020-12) for index.json
tests/catalog/make_catalog.py          TEST-ONLY builder: package zips, index, openssl signature
tests/catalog/keys/                    TEST-ONLY RSA keys + README saying so
tests/catalog/sample/HKGPM/catalogs/sample/   committed sample catalog (stick-shaped)
tests/catalog/test_make_catalog.py     builder reproduces the sample; schema names every field
core/src/.../Hex.java                  hex and SHA-256 helpers
core/src/.../FileFingerprint.java      size + SHA-256 of a file (moved out of StickFs)
core/src/.../CatalogKey.java           PEM -> key, fingerprint, signature check
core/src/.../TrustedKeys.java          trusted fingerprints and their text form
core/src/.../BadCatalogException.java
core/src/.../Glob.java, CarFacts.java, CompatRule.java, Compat.java, Colour.java,
              Verdict.java, CompatCheck.java      compatibility
core/src/.../CatalogIndex.java, CatalogEntry.java   index.json parsing
core/src/.../CatalogView.java, CatalogZip.java, CatalogReader.java   catalogs on a stick
core/src/.../CatalogZips.java          catalog zips -> queue inputs
core/src/.../ZipVerify.java            pre-install size/hash check
app/src/.../CarProbe.java              build id + Wi-Fi on the car
cli/hkgpm_mirror.py                    mirror CLI
tests/cli/test_mirror.py
docs/testing/2026-10-31-phase-2.md     on-car checklist
```

Modified: `StickScanner` (expose `inspect`), `QueueItems`, `QueueSession`, `Messages`,
`ScreenText`, `StickFs`, `StateStore`, `QueueService`, `DriveScreen`, `SettingsScreen`,
`tools/build-app.sh`, `.github/workflows/build.yml`, `README.md`, version files.

---

### Task 1: Format spec, schema, test-only builder and the sample catalog

**Files:**
- Create: `docs/catalog/format.md`, `docs/catalog/catalog.schema.json`
- Create: `tests/catalog/make_catalog.py`, `tests/catalog/keys/README.md`,
  `tests/catalog/keys/test-key.pem`, `tests/catalog/keys/other-test-key.pem`
- Create: `tests/catalog/sample/HKGPM/catalogs/sample/` (generated, committed)
- Test: `tests/catalog/test_make_catalog.py`

**Interfaces:**
- Produces: the field names in the decisions table; the sample stick tree used by Tasks 2,
  4, 5 and 11; `make_catalog.py` used by Tasks 11 and 12.

- [ ] **Step 1: Write `docs/catalog/format.md`.** Sections: folder layout (`index.json`,
  `index.json.sig`, `key.pem`, zips; `remote/<id>.zip` for URL zips on a stick, under
  `HKGPM/catalogs/<id>/`); every field with type, required or optional, and meaning (from
  the spec's "Index contents" and the decisions table); compatibility (OR across rules, AND
  within a rule, rule with no conditions ignored, `build` glob `*`/`?` whole-string
  case-sensitive against `ro.build.product`, `hardware` names with `!`, only `wifi` defined,
  unknown hardware never matches, `conflicts`, red wins over green, the three colours table);
  the signature and fingerprint; "Unknown fields are ignored"; and "Signing a catalog" with
  these commands:

  ```
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out catalog-key.pem
  openssl pkey -in catalog-key.pem -pubout -out key.pem
  openssl pkey -pubin -in key.pem -outform DER | sha256sum      # the fingerprint, for "key"
  openssl dgst -sha256 -sign catalog-key.pem -out index.json.sig index.json
  ```

- [ ] **Step 2: Write `docs/catalog/catalog.schema.json`** (draft 2020-12) matching format.md:
  required index fields `format` (integer, minimum 1), `id` (pattern
  `^(?!\.)(?!.*\.\.)[A-Za-z0-9._-]{1,64}$`), `name`, `publisher`, `generated`, `key` (pattern
  `^[0-9a-f]{64}$`), `packages`; required entry fields `id`, `name`, `version`,
  `description`, `author`, `zip`, `size` (integer ≥ 0), `sha256` (`^[0-9a-fA-F]{64}$`);
  `additionalProperties` left open everywhere.

- [ ] **Step 3: Generate the test keys** (`openssl genpkey ... rsa_keygen_bits:2048`) and write
  `tests/catalog/keys/README.md`: "TEST ONLY. These private keys are public on purpose: they
  sign the sample and on-car test catalogs. Never trust them for real packages."

- [ ] **Step 4: Write `tests/catalog/make_catalog.py`.** CLI:
  `make_catalog.py sample <stick-root>` and `make_catalog.py car <out-dir>` (Task 12 fills
  in `car`; for now it exits 2 "not yet"). Library functions the tests and Task 11 import:

  ```python
  def package_zip(path, pkg_id, name, version, script_body, hook="postinstall") -> None
  def build_catalog(folder, cat_id, name, publisher, packages, key_pem_path,
                    generated="2026-11-01T00:00:00Z", zip_base_url=None) -> str  # returns fingerprint
  ```

  `package_zip` writes a ZIP_STORED zip, entries `NEMESIS.data` then
  `scripts/<hook>.sh`, every entry `date_time=(2026,1,1,0,0,0)`, script
  `external_attr = 0o100755 << 16`, others `0o100644 << 16`; the script starts
  `#!/system/bin/sh`. `packages` is a list of dicts with the entry fields except
  `size`/`sha256` (computed) plus `script` (body); zips go to `zips/<id>.zip`, and with
  `zip_base_url` the entry's `zip` is `<zip_base_url>/<id>.zip` (the zip is still written
  locally, beside, for the test server). `index.json` is `json.dumps(index, indent=2,
  sort_keys=True) + "\n"`, UTF-8. `key.pem` is written with
  `openssl pkey -in <key> -pubout`; fingerprint = SHA-256 of `openssl pkey -pubin -outform
  DER`; signature by `openssl dgst -sha256 -sign`. RSA PKCS#1 v1.5 is deterministic, so the
  sample rebuilds byte for byte.

  The `sample` catalog: id `sample`, name `Sample catalog`, publisher `hkg-package-manager tests`,
  key `test-key.pem`, packages (all version `1.0.0`, script `exit 0`):
  `sample.green` works_on `[{"build":"NEEV23.*"}]`; `sample.wifi` works_on
  `[{"hardware":["wifi"]}]`; `sample.red` not_on `[{"build":"*"}]`; `sample.conflict`
  conflicts `["sample.green"]`; `sample.plain` no compatibility.

- [ ] **Step 5: Write the failing tests** in `tests/catalog/test_make_catalog.py`:

  ```python
  def test_sample_rebuilds_byte_for_byte(self):   # build into a temp dir; every file equals the committed one
  def test_openssl_verifies_sample(self):          # openssl dgst -sha256 -verify key.pem -signature index.json.sig index.json -> exit 0
  def test_schema_names_every_field_the_sample_uses(self):  # every key in index/entries/compatibility/rules appears in the schema's properties
  def test_fingerprint_matches_index_key(self):
  ```

- [ ] **Step 6: Run** `python3 -m unittest discover -s tests/catalog -v`. Expected: FAIL
  (no sample yet).
- [ ] **Step 7: Generate the sample:** `python3 tests/catalog/make_catalog.py sample tests/catalog/sample`.
- [ ] **Step 8: Run the tests again.** Expected: 4 tests OK.
- [ ] **Step 9: Commit** ("Catalog format spec, JSON Schema, test-only builder and a sample catalog").

---

### Task 2: Keys, fingerprints and trusted keys (core)

**Files:**
- Create: `core/src/hkgpackagemanager/core/Hex.java`, `FileFingerprint.java`,
  `CatalogKey.java`, `TrustedKeys.java`, `BadCatalogException.java`
- Modify: `app/src/hkgpackagemanager/app/StickFs.java` (`fingerprint` delegates to `FileFingerprint.of`)
- Test: `core/test/hkgpackagemanager/core/CatalogKeyTest.java`, `TrustedKeysTest.java`

**Interfaces:**
- Produces:
  - `Hex.of(byte[]) -> String` (lowercase); `Hex.sha256(byte[]) -> String`.
  - `FileFingerprint.of(File) -> ZipFingerprint` (`throws IOException`; null when not a file).
  - `BadCatalogException extends Exception` (message = user-visible reason).
  - `CatalogKey.fromPem(String pem) throws BadCatalogException`; fields `byte[] spki`,
    `String fingerprint`; `boolean verifies(byte[] data, byte[] signature)` (false on any error).
  - `TrustedKeys`: `NONE`; `static TrustedKeys decode(String text)` (never throws);
    `String encode()`; `boolean trusts(String fp)`; `TrustedKeys with(String fp, String label)`;
    `TrustedKeys without(String fp)`; `Map<String,String> labels()` (insertion order).

- [ ] **Step 1: Write the failing tests.**

  ```java
  // CatalogKeyTest (reads tests/catalog/sample/HKGPM/catalogs/sample/*; test-java runs from the repo root)
  @Test public void sampleKeyVerifiesSampleIndex()        // fromPem(key.pem).verifies(index bytes, sig bytes) == true
  @Test public void fingerprintIsSha256OfSpki()           // equals the index's "key" value
  @Test public void oneChangedByteFails()                 // flip a byte of the index -> false
  @Test public void otherKeyFails()                       // a KeyPairGenerator("RSA", 2048) key -> false
  @Test(expected = BadCatalogException.class) public void privateKeyPemRejected()   // "PRIVATE KEY" armour
  @Test(expected = BadCatalogException.class) public void shortKeyRejected()        // 1024-bit RSA -> "key too short"
  @Test(expected = BadCatalogException.class) public void ecKeyRejected()           // EC key -> "not an RSA key"
  @Test public void garbageSignatureIsFalseNotThrown()
  // TrustedKeysTest
  @Test public void roundTrip()          // with(fp,"Sample catalog").encode() decodes to the same labels
  @Test public void badLinesSkipped()    // "zz notHex\n\n<fp>\n" -> trusts only fp, label ""
  @Test public void labelLosesNewlinesAndIsCappedAt80()
  @Test public void fingerprintsAreLowercased()
  @Test public void withoutRemoves()
  ```

- [ ] **Step 2: Run `tools/test-java.sh`.** Expected: compile failure (classes missing).
- [ ] **Step 3: Implement.** `CatalogKey.fromPem`: take the text between
  `-----BEGIN PUBLIC KEY-----` and `-----END PUBLIC KEY-----`, decode base64 with a small
  private decoder (ignores whitespace, rejects other characters), `KeyFactory.getInstance("RSA")
  .generatePublic(new X509EncodedKeySpec(der))`, refuse modulus bit length < 2048. `verifies`:
  `Signature.getInstance("SHA256withRSA")`. Text form of `TrustedKeys`: one
  `<64 hex> <label>` per line. Move the hashing loop from `StickFs.fingerprint` into
  `FileFingerprint.of` and make `StickFs` call it.
- [ ] **Step 4: Run `tools/test-java.sh` and `sh tests/app/check-app.sh`.** Expected: all OK
  (check-app proves the core still compiles against android.jar).
- [ ] **Step 5: Commit** ("Catalog keys: PEM parsing, fingerprints, signature check and the trusted-key list").

---

### Task 3: Compatibility rules (core)

**Files:**
- Create: `core/src/hkgpackagemanager/core/Glob.java`, `CarFacts.java`, `CompatRule.java`,
  `Compat.java`, `Colour.java`, `Verdict.java`, `CompatCheck.java`
- Test: `core/test/hkgpackagemanager/core/GlobTest.java`, `CompatCheckTest.java`

**Interfaces:**
- Produces:
  - `Glob.matches(String pattern, String text) -> boolean`.
  - `CarFacts(String buildId, Boolean wifi)`; `static Boolean wifiFrom(boolean hasWlan, String kernelLog)`;
    `static final String WIFI_FAIL_LINE = "failed to power up DHD generic adapter, max retry reached"`.
  - `CompatRule(String build, List<String> hardware)` (either may be null); `boolean isValid()`;
    `boolean matches(CarFacts car)`.
  - `Compat(List<CompatRule> worksOn, List<CompatRule> notOn, List<String> conflicts)`;
    `static final Compat NONE`; `boolean isEmpty()`.
  - `enum Colour { GREEN, YELLOW, RED }`; `Verdict(Colour colour, String reason)`.
  - `CompatCheck.check(Compat c, CarFacts car, Set<String> installedIds) -> Verdict`;
    `CompatCheck.LOOSE` = `new Verdict(YELLOW, "Not from a catalog")`.
  - Reasons (exact): `"Works on this car"`, `"No compatibility information"`,
    `"Not listed for this car"`, `"Marked as not working on this car"`,
    `"Conflicts with installed " + ids joined with ", "` (sorted).

- [ ] **Step 1: Write the failing tests.**

  ```java
  // GlobTest
  assertTrue(Glob.matches("NEEV23.*", "NEEV23.USA.0000.V015.012.260507"));
  assertFalse(Glob.matches("NEEV23.*", "xNEEV23.USA"));      // whole string
  assertFalse(Glob.matches("neev23.*", "NEEV23.USA"));       // case-sensitive
  assertTrue(Glob.matches("NE?V23*", "NEEV23"));
  assertTrue(Glob.matches("*", ""));  assertFalse(Glob.matches("?", ""));
  assertTrue(Glob.matches("a*b*c", "aXbYbZc"));
  assertTrue(Glob.matches(repeat("*a", 30), repeat("a", 30)));   // finishes fast (no exponential backtracking)
  // CompatCheckTest; CAR = new CarFacts("NEEV23.USA.0000.V015.012.260507", true)
  @Test public void noDataIsYellow()                         // Compat.NONE -> YELLOW "No compatibility information"
  @Test public void buildMatchIsGreen()
  @Test public void noMatchEitherWayIsYellowNotListed()
  @Test public void negativeBuildIsRed()
  @Test public void redWinsOverGreen()
  @Test public void conflictInstalledIsRed()                 // reason "Conflicts with installed a.b, c.d"
  @Test public void conflictNotInstalledIgnored()
  @Test public void ruleNeedsAllConditions()                 // build matches but hardware ["!wifi"] -> no match
  @Test public void ruleWithNoConditionsIgnored()            // works_on [empty rule] -> YELLOW
  @Test public void unknownHardwareNeverMatches()            // works_on [{hardware:["bluetooth"]}] -> YELLOW; not_on same -> YELLOW
  @Test public void unknownWifiNeverMatches()                // CarFacts(build, null): works_on wifi -> YELLOW, not_on !wifi -> YELLOW
  @Test public void unknownBuildNeverMatchesBuildRules()     // CarFacts("", true): works_on {build:"*"} -> YELLOW
  @Test public void hardwareOnlyRuleIsValid()                // not_on [{hardware:["!wifi"]}] on CarFacts(b,false) -> RED
  @Test public void wifiFromWlanKernelLog()                  // (true, null)->TRUE; (false, "..."+WIFI_FAIL_LINE+"...")->FALSE; (false,"x")->null; (false,null)->null
  ```

- [ ] **Step 2: Run `tools/test-java.sh`.** Expected: compile failure.
- [ ] **Step 3: Implement.** `Glob` uses the linear two-pointer wildcard match (remember the
  last `*` and the text position it matched; on mismatch advance from there), not recursion.
  `CompatRule.matches`: false if invalid; `build` must be non-null and match a non-empty
  `buildId`; each hardware name `wifi`/`!wifi` needs `car.wifi` non-null and equal to the
  wanted value; any other name -> false. `CompatCheck`: red if a valid not_on rule matches or
  a conflict id is installed (conflict reason wins the wording when both), else green if a
  valid works_on rule matches, else yellow ("No compatibility information" when `isEmpty()`,
  else "Not listed for this car").
- [ ] **Step 4: Run `tools/test-java.sh`.** Expected: all OK.
- [ ] **Step 5: Commit** ("Compatibility rules: glob, car facts, and the green/yellow/red verdict").

---

### Task 4: Parse `index.json` (core)

**Files:**
- Create: `core/src/hkgpackagemanager/core/CatalogIndex.java`, `CatalogEntry.java`
- Test: `core/test/hkgpackagemanager/core/CatalogIndexTest.java`

**Interfaces:**
- Consumes: `BadCatalogException`, `Compat`, `CompatRule`, `PackageId.isValid`.
- Produces:
  - `CatalogIndex.parse(byte[] bytes) throws BadCatalogException`; constants
    `SUPPORTED_FORMAT = 1`, `MAX_BYTES = 2 * 1024 * 1024`; fields `int format`, `String id,
    name, publisher, generated, key`, `List<CatalogEntry> entries`.
  - `CatalogEntry`: fields `String id, name, version, description, author, homepage, zip`,
    `long size`, `String sha256` (lowercased), `Compat compat`, `String problem` (null when
    valid; when set, other fields may be null); `String localPath()` (catalog-folder-relative;
    null when invalid); `boolean isRemote()`.

- [ ] **Step 1: Write the failing tests** (build JSON strings inline; plus the committed sample).

  ```java
  @Test public void parsesSample()                     // 5 entries, sample.green compat.worksOn.get(0).build == "NEEV23.*"
  @Test public void newerFormatRefused()               // format 2 -> message "This catalog needs a newer HKG Package Manager (format 2)."
  @Test public void missingHeaderFieldRefused()        // no "publisher" -> message names publisher
  @Test public void invalidCatalogIdRefused()          // "id": "../x"
  @Test public void notJsonRefused()                   // message "index.json is not valid JSON"
  @Test public void bomIsAccepted()                    // EF BB BF prefix parses
  @Test public void tooBigRefused()                    // MAX_BYTES + 1 bytes -> refused before parsing
  @Test public void wrongTypesInvalidateOnlyThatEntry() // entry with "size":"12" -> problem "invalid catalog entry: size"; the other entry stays valid
  @Test public void badCompatibilityInvalidatesEntry()  // "not_on": "x" -> problem names not_on
  @Test public void duplicateIdSecondIsInvalid()        // problem "duplicate package id in this catalog"
  @Test public void unknownFieldsIgnored()
  @Test public void zipLocationThatEscapesIsInvalid()   // "../x.zip", "/system/x.zip", "a/../../x.zip", "a\\b.zip", "", "a//b.zip", "remote/x.zip", "ftp://h/x.zip" -> problem "unsupported zip location"
  @Test public void urlZipStoredUnderRemote()           // "https://h/p/x.zip" for id a.b -> localPath "remote/a.b.zip", isRemote
  @Test public void badShaInvalid()                     // 63 hex chars -> problem names sha256
  ```

- [ ] **Step 2: Run `tools/test-java.sh`.** Expected: compile failure.
- [ ] **Step 3: Implement** with `org.json` (`JSONObject`, `JSONArray`; use only methods
  android-19 has: `opt`, `optJSONArray`, `getString` etc., checked by `check-app.sh`). A
  field's type check uses `instanceof` on `opt(name)` (String, Integer/Long, JSONArray,
  JSONObject); `size` accepts Integer or Long ≥ 0.
- [ ] **Step 4: Run `tools/test-java.sh` and `sh tests/app/check-app.sh`.** Expected: all OK.
- [ ] **Step 5: Commit** ("Parse catalog index.json: header, entries, compatibility and zip locations").

---

### Task 5: Catalogs on a stick (core)

**Files:**
- Create: `core/src/hkgpackagemanager/core/CatalogView.java`, `CatalogZip.java`, `CatalogReader.java`
- Modify: `core/src/hkgpackagemanager/core/StickScanner.java` (make `inspect` public as
  `public static StickZip inspect(File f, String relPath)`)
- Test: `core/test/hkgpackagemanager/core/CatalogReaderTest.java`

**Interfaces:**
- Consumes: Tasks 2-4.
- Produces:
  - `CatalogReader.DIR = "HKGPM/catalogs"`; `interface Fingerprinter { ZipFingerprint of(File f) throws IOException; }`;
    `static List<CatalogView> read(File stickRoot, TrustedKeys trusted, Fingerprinter fp)`
    (folders sorted by name, case-insensitive; hidden names and plain files skipped silently).
  - `CatalogView`: `enum Trust { TRUSTED, UNTRUSTED, BROKEN }`; fields `String folder`
    (stick-relative, e.g. `HKGPM/catalogs/sample`), `Trust trust`, `String error` (BROKEN only),
    `CatalogIndex index` (null when BROKEN), `String fingerprint` (null when the key is
    unreadable), `List<CatalogZip> zips` (TRUSTED only).
  - `CatalogZip`: fields `CatalogEntry entry`, `String catalogName`, `String relPath`
    (stick-relative; null when the entry is invalid), `StickZip zip` (inspected; null when not
    on the drive or invalid), `String problem`; `boolean onDrive()`; `boolean installable()`
    (on drive and no problem). Constants `NOT_ON_DRIVE = "not on this drive"`,
    `ZIP_MISMATCH = "zip doesn't match its catalog entry (id or version)"`.
  - Broken-row reasons (exact): `"no key.pem"`, `"no index.json"`, `"no index.json.sig"`,
    `"index.json is too big"`, the `BadCatalogException` message for a bad key or index,
    `"signature doesn't match: the catalog was changed or damaged"`,
    `"index.json names a different key"`, `"folder name doesn't match catalog id " + id`.

- [ ] **Step 1: Write the failing tests** (copy the committed sample tree into a temp stick
  root; tamper copies there; sample fingerprint read from its `index.json`).

  ```java
  @Test public void trustedSampleListsFiveInstallableZips()
  @Test public void untrustedKeyGivesUntrustedWithFingerprintAndNoZips()
  @Test public void tamperedIndexIsBroken()                // append a space to index.json -> signature reason
  @Test public void tamperedZipIsNotInstallable()          // change one byte of zips/sample.plain.zip -> problem Messages.MSG_CATALOG_MISMATCH
  @Test public void missingZipIsNotOnDrive()               // onDrive() false, installable() false, problem == NOT_ON_DRIVE
  @Test public void zipWithOtherVersionIsMismatch()        // re-sign an index whose entry says 1.0.1 for a 1.0.0 zip -> ZIP_MISMATCH
                                                           // (test-only signing: read tests/catalog/keys/test-key.pem as PKCS#8 with java.util.Base64, SHA256withRSA)
  @Test public void messyCatalogFolderGivesOneBrokenRowEach() // stray file + folder without sig + folder without key + renamed folder: 3 BROKEN rows with their reasons, the sample still TRUSTED
  @Test public void oversizedIndexIsBroken()
  @Test public void noCatalogFolderGivesEmptyList()
  @Test public void signatureCheckedBeforeParsing()        // garbage index.json with a garbage sig -> signature reason, not the JSON reason
  ```

  Add `Messages.MSG_CATALOG_MISMATCH = "doesn't match the catalog (size or SHA-256)"` in this task.

- [ ] **Step 2: Run `tools/test-java.sh`.** Expected: compile failure.
- [ ] **Step 3: Implement** in this order per folder: key.pem, index.json, index.json.sig
  present; size ≤ `MAX_BYTES`; `CatalogKey.fromPem`; `verifies`; `CatalogIndex.parse`;
  `index.key` equals fingerprint; folder name equals `index.id`; trust. Per valid entry:
  file at `folder/localPath()` missing -> NOT_ON_DRIVE; `fp.of` size or sha differs ->
  `MSG_CATALOG_MISMATCH`; `StickScanner.inspect` problem -> that problem; id or trimmed
  version differs -> `ZIP_MISMATCH`. `inspect` gets the stick-relative path
  (`folder + "/" + localPath()`), so `CatalogZip.zip.relPath` is what the queue looks up.
- [ ] **Step 4: Run `tools/test-java.sh`.** Expected: all OK.
- [ ] **Step 5: Commit** ("Read catalogs from the stick: trust, signature, broken rows and per-zip checks").

---

### Task 6: Catalog zips in the queue (core)

**Files:**
- Create: `core/src/hkgpackagemanager/core/CatalogZips.java`, `ZipVerify.java`
- Modify: `core/src/hkgpackagemanager/core/QueueItems.java`, `QueueSession.java`
- Modify: `core/test/hkgpackagemanager/core/QueueItemsTest.java`, `QueueSessionTest.java`
- Test: `core/test/hkgpackagemanager/core/ZipVerifyTest.java`, `CatalogZipsTest.java`

**Interfaces:**
- Consumes: `CatalogView`, `CatalogZip`, `ZipCheck`, `ZipFingerprint`, `Messages.MSG_CATALOG_MISMATCH`.
- Produces:
  - `CatalogZips.zips(List<CatalogView>) -> List<StickZip>` and
    `CatalogZips.expected(List<CatalogView>) -> Map<String, ZipFingerprint>` (keyed by
    relPath; installable zips of TRUSTED catalogs only).
  - `QueueItems.build(List<StickZip> scanned, Map<String, ZipFingerprint> expected, String[] paths, boolean[] updates)`
    replaces the 3-argument form: an expected entry sets the WAITING item's `size` and
    `sha256`; others keep `0` / `""`.
  - `QueueSession.start`: a WAITING item with a non-empty `sha256` whose fingerprint differs
    in size or hash becomes FAILED `MSG_CATALOG_MISMATCH`. Items with `""` behave as in Phase 1.
  - `ZipVerify.problem(ZipCheck check, String relPath, long size, String sha256) -> String`
    (`MSG_MISSING` if missing, `MSG_CATALOG_MISMATCH` if `sha256` non-empty and size or hash
    differs, else null; `throws IOException`).

- [ ] **Step 1: Write the failing tests.**

  ```java
  // QueueItemsTest
  @Test public void expectedFingerprintFillsSizeAndHash()
  @Test public void looseZipKeepsEmptyHash()
  // QueueSessionTest (MemStickFiles + a fake ZipCheck as the existing tests do)
  @Test public void catalogItemWithChangedHashFailsAtStart()   // state FAILED, message MSG_CATALOG_MISMATCH, nothing installed
  @Test public void catalogItemWithMatchingHashInstalls()
  // ZipVerifyTest
  @Test public void zipVerifyCatchesSwap()      // expected sha A, check returns sha B -> MSG_CATALOG_MISMATCH
  @Test public void zipVerifyMissing()          // check returns null -> MSG_MISSING
  @Test public void zipVerifyLooseIsNull()      // sha "" -> null
  // CatalogZipsTest
  @Test public void onlyInstallableZipsOfTrustedCatalogs()
  ```

- [ ] **Step 2: Run `tools/test-java.sh`.** Expected: compile failure.
- [ ] **Step 3: Implement**, and update the one app caller (`QueueService`) to pass
  `Collections.<String, ZipFingerprint>emptyMap()` for now (Task 8 passes the real map).
- [ ] **Step 4: Run `tools/test-java.sh` and `sh tests/app/check-app.sh`.** Expected: all OK.
- [ ] **Step 5: Commit** ("Queue catalog zips with their expected hash and refuse a zip that no longer matches").

---

### Task 7: Screen wording for colours, trust and confirmations (core)

**Files:**
- Modify: `core/src/hkgpackagemanager/core/ScreenText.java`
- Modify: `core/test/hkgpackagemanager/core/ScreenTextTest.java`

**Interfaces:**
- Produces (exact copy):
  - `YELLOW_WARNING = "This package hasn't been checked for this car."`
  - `RED_WARNING = "This package is marked as not working on this car."`
  - `RED_TICK = "I understand; install it anyway"`
  - `LOOSE_HEADER = "Not from a catalog"`
  - `TRUST_BUTTON = "Trust this key"`
  - `static String fingerprint(String hex)` -> groups of 4 separated by spaces.
  - `static String untrustedLine(String name, String hex)` -> `"Untrusted catalog " + name + ", key fingerprint " + fingerprint(hex)`.
  - `static String trustConfirm(String name, String hex)` -> `"Trust catalogs signed with this key?\n\n" + name + "\n" + fingerprint(hex) + "\n\nCheck the fingerprint with the catalog's publisher first."`
  - `static String singleConfirm(String action, PackageMeta meta, Verdict v)`: the existing
    text, then for YELLOW `"\n\n" + YELLOW_WARNING + " (" + v.reason + ")"`, for RED
    `"\n\n" + RED_WARNING + " (" + v.reason + ")"`; GREEN adds nothing. It replaces the
    2-argument form (update its test); loose zips pass `CompatCheck.LOOSE`.
  - `static String queueConfirm(List<String> lines, List<String> yellowNames, int redCount)`:
    `"Install these packages?\n"` + `"\n" + line` each, then if yellows
    `"\n\nNot checked for this car: " + names joined ", "`, then if `redCount > 0`
    `"\n\nTick each package marked as not working on this car to continue."`.
  - `static String verdictLine(Verdict v)` -> `v.reason`.

- [ ] **Step 1: Write the failing tests** for each function above with the exact strings
  (for example `fingerprint("0123456789abcdef")` is `"0123 4567 89ab cdef"`).
- [ ] **Step 2: Run `tools/test-java.sh`.** Expected: compile failure.
- [ ] **Step 3: Implement.**
- [ ] **Step 4: Run `tools/test-java.sh`.** Expected: all OK.
- [ ] **Step 5: Commit** ("Wording for catalog colours, untrusted keys and the gated confirmations").

---

### Task 8: App plumbing: car facts, trusted keys store, catalog-aware queue start

**Files:**
- Create: `app/src/hkgpackagemanager/app/CarProbe.java`
- Modify: `app/src/hkgpackagemanager/app/StateStore.java`, `QueueService.java`

**Interfaces:**
- Consumes: `CarFacts`, `TrustedKeys`, `CatalogReader`, `CatalogZips`, `FileFingerprint`.
- Produces:
  - `CarProbe.facts(Nemesis n) -> CarFacts`: cached for the process. Build id from running
    `/system/bin/getprop ro.build.product` with `Runtime.exec` (no root; trimmed; "" on any
    failure). Wi-Fi: `hasWlan` = any `/sys/class/net/wlan*`; only when false, kernel log from
    `n.root("dmesg")` (null when exit ≠ 0); then `CarFacts.wifiFrom`.
  - `StateStore.KEYS_PATH = STATE_DIR + "/keys.txt"`; `TrustedKeys loadKeys()` and
    `void saveKeys(TrustedKeys k)` with the same root-copy-plus-cache pattern as settings
    (cache file `keys.txt` in filesDir). Refactor the shared code into private
    `loadText(String rootPath, File cache)` / `saveText(...)` rather than copying it.
  - `QueueService` ACTION_START: `scan` = `StickScanner.scan(root)` plus
    `CatalogZips.zips(views)`, `expected` = `CatalogZips.expected(views)`, where
    `views = CatalogReader.read(new File(root), new StateStore(app, cli).loadKeys(), fp)`, where
    `fp` is a `CatalogReader.Fingerprinter` that calls `FileFingerprint.of`.
- No new unit tests (Android classes); the core pieces are tested. `check-app.sh` must pass.

- [ ] **Step 1: Implement the three changes.** `QueueService` needs no fingerprint cache.
- [ ] **Step 2: Run `tools/test-java.sh && sh tests/app/check-app.sh`.** Expected: all OK.
- [ ] **Step 3: Commit** ("Probe the car's build and Wi-Fi, store trusted catalog keys, queue catalog zips").

---

### Task 9: Drive screen with catalogs and colour-gated installs

**Files:**
- Modify: `app/src/hkgpackagemanager/app/DriveScreen.java`, `app/src/hkgpackagemanager/app/Ui.java`

**Interfaces:**
- Consumes: Tasks 5-8.
- Behaviour (the spec's "The manager with a catalog on the stick" and the colours table):
  - Background load adds `views = CatalogReader.read(stick, keys, cachingFingerprinter)` and
    `facts = CarProbe.facts(cli)`; installed ids come from the existing `pkg-list` call, and
    installed versions are read for catalog ids too.
  - The caching fingerprinter is a field of `DriveScreen`: a map from relPath to
    `{size, lastModified, ZipFingerprint}`; reuse when size and mtime are unchanged.
  - Render order: drive picker, error, the Select all / Install selected bar; then each
    catalog: TRUSTED -> bold `name`, small `publisher`, then one row per `CatalogZip`;
    UNTRUSTED -> `ScreenText.untrustedLine` and a `TRUST_BUTTON` that opens
    `trustConfirm` and on yes saves `keys.with(fp, index.name)` then refreshes; BROKEN ->
    bold folder name and the error in grey. Then the `LOOSE_HEADER` section with the Phase 1
    rows (only when there are loose zips or no catalogs).
  - A catalog row: checkbox (installable only), bold name, `id   version`, description,
    the verdict line in its colour (`Ui.GREEN = 0xFF2E9E44`, `Ui.YELLOW = 0xFFC9A100`,
    `Ui.RED = 0xFFD03030`, added to `Ui`), status (`ScreenText.statusText` on the inspected
    zip; `NOT_ON_DRIVE` or the problem in grey when not installable), and the Install/Update
    button when installable. Loose rows show the `CompatCheck.LOOSE` line in yellow.
  - Single install: confirm with `ScreenText.singleConfirm(action, meta, verdict)`. RED: the
    dialog carries a `CheckBox` with `RED_TICK`, and its positive button is disabled until
    it's ticked (add `Ui.confirmWithTick(Activity, String message, String tick, String yes, Runnable onYes)`).
    The worker runs `ZipVerify.problem(new StickFs(...), relPath, size, sha256)` first and
    shows the problem instead of installing when it isn't null.
  - Install selected: `ScreenText.queueConfirm(lines, yellowNames, redCount)`; when
    `redCount > 0` the dialog shows one `CheckBox` per red package (label = its name) and
    Install stays disabled until all are ticked. Rows keyed by relPath, so the same id from
    two catalogs (or a catalog and a loose zip) are two rows. Select all ticks every
    installable row except red ones (each red row is ticked by hand).
- No new unit tests; manual check is the on-car checklist.

- [ ] **Step 1: Implement.**
- [ ] **Step 2: Run `tools/test-java.sh && sh tests/app/check-app.sh`.** Expected: all OK.
- [ ] **Step 3: Commit** ("Drive screen: catalog sections, colours, trust prompt and gated installs").

---

### Task 10: Settings: catalog keys

**Files:**
- Modify: `app/src/hkgpackagemanager/app/SettingsScreen.java`

**Interfaces:**
- Consumes: `StateStore.loadKeys/saveKeys`, `CatalogKey`, `ScreenText.fingerprint`, `trustConfirm`.
- Behaviour: a "Catalog keys" section under the button settings: "No trusted keys." or
  one row per key (label, `fingerprint(fp)` in grey, a Remove button with confirm
  `"Stop trusting " + label + "?"`). Button "Add keys from the USB drive": reads
  `HKGPM/keys/*.pem` on the chosen stick (`ScreenText.chooseStick`); for each file that
  parses and isn't trusted, one `trustConfirm(fileName, fp)` dialog in turn; a file that
  doesn't parse gives `fileName + ": " + reason`. Result line: `"Added N key(s)."`,
  `"No new keys in HKGPM/keys on the drive."` or `"No USB drive found."`.

- [ ] **Step 1: Implement.**
- [ ] **Step 2: Run `tools/test-java.sh && sh tests/app/check-app.sh`.** Expected: all OK.
- [ ] **Step 3: Commit** ("Settings: list, remove and add trusted catalog keys").

---

### Task 11: Mirror CLI

**Files:**
- Create: `cli/hkgpm_mirror.py`, `cli/README.md`
- Test: `tests/cli/test_mirror.py`

**Interfaces:**
- Consumes: the format (Task 1); `tests/catalog/make_catalog.py` (`build_catalog`,
  `package_zip`) for test catalogs.
- Produces: `python3 cli/hkgpm_mirror.py SOURCE STICK [--only ID ...] [--fingerprint HEX]`
  and `main(argv) -> int`. SOURCE is a catalog folder path or an `http(s)://` URL of the
  folder (files fetched as `SOURCE/index.json`, `/index.json.sig`, `/key.pem`). Exit codes:
  0 done, 1 error, 2 usage, 3 untrusted key. Config dir: `$HKGPM_CONFIG_DIR`, else
  `$XDG_CONFIG_HOME/hkg-package-manager`, else `~/.config/hkg-package-manager`; file
  `trusted-keys.txt` in the `TrustedKeys` text form.
- Library functions (tested directly):

  ```python
  def spki_rsa(der: bytes) -> tuple          # (n, e); ValueError unless rsaEncryption and n >= 2048 bits
  def verify(key_pem: bytes, data: bytes, sig: bytes) -> bool
  def fingerprint(key_pem: bytes) -> str
  ```

  `verify` is RSASSA-PKCS1-v1_5: `k` = byte length of `n`; `len(sig) == k`;
  `m = pow(int.from_bytes(sig,'big'), e, n).to_bytes(k,'big')`; expected =
  `b'\x00\x01' + b'\xff' * (k - 3 - len(T)) + b'\x00' + T` with
  `T = bytes.fromhex('3031300d060960864801650304020105000420') + sha256(data)`;
  compare with `hmac.compare_digest`. `spki_rsa` walks the DER (SEQUENCE { SEQUENCE {
  OID `06092a864886f70d010101`, NULL }, BIT STRING { SEQUENCE { INTEGER n, INTEGER e } } }).
- Run order: fetch index, sig, key into memory; key fingerprint must equal the index's
  `key`; trust check (stored, or `--fingerprint` equal to it, which then gets stored with the
  catalog name as label; otherwise print `untrusted key <fp>: check it with the publisher,
  then re-run with --fingerprint <fp>` and exit 3); `verify` or exit 1 "signature doesn't
  match"; parse; `id` valid; refuse format > 1. Target folder `STICK/HKGPM/catalogs/<id>/`.
  For each wanted entry (all, or `--only` ids; an unknown `--only` id is exit 2): skip if the
  local file already has the right size and SHA-256; else download (relative zips from
  SOURCE, URLs as given) to `<local>.part`, check size and SHA-256 (mismatch: delete the
  part, exit 1 naming the id), then `os.replace` onto the final name. Apply the same zip
  location rules as Task 4 (an invalid entry is skipped with a warning). Then write
  `key.pem`, `index.json.sig` and `index.json` (last, each via `.part` + `os.replace`),
  and delete files under the target folder that aren't one of those three or a wanted
  entry's local path (`.part` leftovers included). Prints one line per zip
  (`fetched`, `kept`, `removed`) and a summary.

- [ ] **Step 1: Write the failing tests** (`unittest`; catalogs built in temp dirs with
  `make_catalog.build_catalog` and the test key; URL tests serve a temp dir with
  `http.server.ThreadingHTTPServer` on 127.0.0.1 port 0 in a thread).

  ```python
  def test_verify_matches_openssl_on_sample(self)
  def test_verify_rejects_flipped_byte(self)
  def test_spki_rejects_short_key(self)
  def test_mirror_local_catalog(self)                 # stick tree equals source files; trusted via --fingerprint and stored
  def test_untrusted_key_exits_3_and_writes_nothing(self)
  def test_stored_key_needs_no_flag(self)
  def test_tampered_index_exits_1_and_writes_nothing(self)
  def test_tampered_zip_exits_1_and_keeps_old_stick(self)   # the stick's previous index stays
  def test_subset_keeps_full_index(self)              # --only sample.green: one zip, full index.json
  def test_rerun_removes_dropped_zip(self)            # source drops sample.plain -> its zip removed
  def test_url_catalog_and_url_zip(self)              # SOURCE over HTTP, entry zip as absolute URL -> remote/<id>.zip
  def test_interruptedDownloadLeavesNoFinalFile(self) # server sends half the bytes for one zip then closes -> exit 1, no final file for it; re-run with a good server completes
  def test_unknown_only_id_exits_2(self)
  ```

- [ ] **Step 2: Run** `python3 -m unittest discover -s tests/cli -v`. Expected: FAIL (no module).
- [ ] **Step 3: Implement** with `urllib.request`, `hashlib`, `hmac`, `json`, `argparse`.
- [ ] **Step 4: Run the tests.** Expected: 13 tests OK.
- [ ] **Step 5: Write `cli/README.md`**: usage, trust, exit codes, the stick layout.
- [ ] **Step 6: Commit** ("Mirror CLI: copy a signed catalog onto a USB stick, verified, with no extra packages").

---

### Task 12: On-car catalogs, checklist, CI, docs and version 1.1.0

**Files:**
- Modify: `tests/catalog/make_catalog.py` (the `car` subcommand)
- Create: `docs/testing/2026-10-31-phase-2.md`
- Modify: `tools/build-app.sh`, `app/AndroidManifest.xml`, `package/NEMESIS.data`,
  `.github/workflows/build.yml`, `README.md`, the spec's status line

**Interfaces:**
- Produces: `python3 tests/catalog/make_catalog.py car dist/test-catalogs` writing stick-ready
  trees (each is copied to a stick root as is):
  - `trusted/`: `HKGPM/catalogs/cartest/` signed with `test-key.pem`, packages (script writes
    `/data/nemesis/<id>.marker`, `exit 0`): `cartest.green` (works_on build `NEEV23.*`),
    `cartest.wifi` (works_on hardware `wifi`), `cartest.yellow` (none), `cartest.red`
    (not_on build `*`), `cartest.conflict` (conflicts `test.a`), `cartest.restart`
    (works_on build `*`, script `exec /system/bin/reboot`); plus `HKGPM/keys/test-key.pem`
    (the public key, for Settings > Add keys) and a loose `test.a.zip` at the root (built with
    `package_zip`, same contents as Phase 1's test.a).
  - `tampered-index/`: `trusted/`'s catalog with one byte of `index.json` changed after signing.
  - `tampered-zip/`: `trusted/`'s catalog with one byte of `zips/cartest.yellow.zip` changed.
  - `unknown-key/`: the same catalog signed with `other-test-key.pem` (id `othertest`).

- [ ] **Step 1: Implement `car`** and add a test to `tests/catalog/test_make_catalog.py`:
  `test_car_catalogs_have_expected_states` (openssl verifies `trusted` and `unknown-key`,
  fails `tampered-index`; `tampered-zip`'s zip hash differs from its index).
- [ ] **Step 2: Version 1.1.0.** `app/AndroidManifest.xml` versionName `1.1.0`, versionCode
  `4`; `package/NEMESIS.data` `version=1.1.0`. In `tools/build-app.sh`, pass
  `--version-name/--version-code/--replace-version` only when `VERSION_NAME` or
  `VERSION_CODE` is set, so the manifest is the one source of the version; update its header
  comment. Update `tests/app/check-app.sh` and the package test if they assert 1.0.0 or 1.
- [ ] **Step 3: CI.** In `.github/workflows/build.yml` "Host tests", add
  `python3 -m unittest discover -s tests/catalog -v` and
  `python3 -m unittest discover -s tests/cli -v`; after "Build package", run
  `python3 tests/catalog/make_catalog.py car dist/test-catalogs` and zip it as
  `dist/hkg-test-catalogs.zip` so it's attached to runs (and releases) beside the package.
- [ ] **Step 4: Write `docs/testing/2026-10-31-phase-2.md`** in the Phase 1 checklist's style
  (expected on-screen text quoted exactly from `ScreenText`/`CatalogReader`), covering the
  spec's list: a trusted catalog (Trust this key from the drive screen, check the
  fingerprint); Settings > Add keys from `HKGPM/keys` and Remove; a tampered index (broken
  row with the signature reason); a tampered zip (row not installable with the mismatch
  reason); an unknown key (untrusted row, no packages); each colour (green with no extra
  warning, yellow with the warning, red needing the tick; conflict turning red only after
  test.a is installed; the Wi-Fi row green on a car with Wi-Fi); a mixed queue of green,
  yellow, red, a loose zip and `cartest.restart` (one confirmation naming the yellows, a
  tick per red, survives the restart, "Finished: …"); the mirror CLI writing a stick from
  `dist/test-catalogs/trusted/HKGPM/catalogs/cartest` (local path source) and the result
  listing the same; a self-update from 1.0.x to 1.1.0 keeping settings and keys.
- [ ] **Step 5: Docs.** README: Phase 2 status paragraph, links to `docs/catalog/format.md`,
  `cli/README.md` and the new checklist, the Python test commands in "Full test run". Spec
  status line: "Phase 1 released (v1.0.0); Phase 2 built, not yet tried on the car".
- [ ] **Step 6: Full run:**
  `tools/test-java.sh && sh tests/kit/run.sh && sh tests/resume/run.sh && sh tests/app/check-app.sh && tools/build-package.sh && sh tests/package/run.sh && python3 -m unittest discover -s tests/catalog && python3 -m unittest discover -s tests/cli`.
  Expected: every suite OK.
- [ ] **Step 7: Commit** ("Phase 2 on-car catalogs and checklist, CI for the Python tests, version 1.1.0").
