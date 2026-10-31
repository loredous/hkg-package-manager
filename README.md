# hkg.package.manager

A NEMESIS package manager for Hyundai/Kia Gen5W head units. It installs and uninstalls
NEMESIS packages from a USB stick, with an on-screen app to drive it (a drive tab, an
installed tab, a progress screen and settings), a queue that survives head unit restarts, and
a car button (Media or Navigation, pressed three times) that opens it.

**Status: Phase 1 built, not yet tried on the car.** The Media button default uses provisional
broadcast names until the key-capture kit has run; the manager always opens from NEMESIS
Manager > Packages > HKG Package Manager > Launch.

- Design: [docs/superpowers/specs/2026-10-30-hkg-package-manager-design.md](docs/superpowers/specs/2026-10-30-hkg-package-manager-design.md)
- Plan: [docs/superpowers/plans/2026-10-30-phase-1-package-manager.md](docs/superpowers/plans/2026-10-30-phase-1-package-manager.md)
- On-car checklist: [docs/testing/2026-10-30-phase-1.md](docs/testing/2026-10-30-phase-1.md)
- Key capture notes: [docs/notes/key-capture.md](docs/notes/key-capture.md)

## Build

Prerequisites (Linux host): Android SDK at `~/Android/sdk` with platform `android-19` and
build-tools `30.0.3`; JDK 17; `zip`; `python3`. `mksh` is optional (tests fall back to
`bash --posix`). `../hmg_nemesis` (sibling checkout) is used by `tools/build-test-packages.sh`.

1. `tools/toolchain.sh fetch` (test jars into `private/toolchain/`)
2. `tools/toolchain.sh check`: every line should say `ok`
3. `tools/test-java.sh`
4. `tools/build-app.sh` (first run creates the signing key in `private/keys/`)
5. `tools/build-package.sh` (writes `dist/hkg-package-manager.zip`)
6. `tools/build-kit.sh` (writes `dist/hkg-keycap-kit.zip`, the read-only key-capture kit)
7. Optional: `tools/build-test-packages.sh` (writes `dist/test-packages/` for the checklist)

**Back up `private/keys/hkgpm.jks` and its password file.** Android only accepts an update
signed with the same key, so without them the next version can't update this install.

Full test run:

    tools/test-java.sh && sh tests/kit/run.sh && sh tests/resume/run.sh && sh tests/app/check-app.sh && sh tests/package/run.sh

Shell lint (head unit shell has no `printf`, `tail`, `head`, `seq`, `awk`, `grep`, `[[ ]]`):
`tools/lint-sh.sh kit package stick`.

## Install

Copy the contents of `dist/hkg-package-manager.zip` (`NEMESIS.data` and `packages/`) to the root of
a USB stick and plug it into the car. NEMESIS installs it. It needs NEMESIS with its command
channel running. Then follow the checklist.
