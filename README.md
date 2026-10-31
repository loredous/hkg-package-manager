# hkg.package.manager

A NEMESIS package manager for Hyundai/Kia Gen5W head units. It installs and uninstalls
NEMESIS packages from a USB stick, with an on-screen app to drive it (a drive tab, an
installed tab, a progress screen and settings), a queue that survives head unit restarts, and
a car button that opens it (hold Media by default, or press Navigation a set number of times).

**Status: Phase 1 built, not yet tried on the car.** The opening button comes from the
owner's key capture ([docs/notes/key-capture.md](docs/notes/key-capture.md)). The manager can
also always be opened from NEMESIS Manager > Packages > HKG Package Manager > Launch.

**Phase 2 (version 1.1.0) built, not yet tried on the car.** It adds signed package catalogs
on the stick (`HKGPM/catalogs/<id>/`), trusted keys, a green/yellow/red compatibility check
before installing, and `cli/hkgpm_mirror.py` to copy a catalog onto a stick from a desktop.
Stick-only use with loose zips works as in Phase 1. The on-car test catalogs come from
`python3 tests/catalog/make_catalog.py car dist/test-catalogs` (test-only keys; the CI
artifact `hkg-test-catalogs-*` holds them too).

- Design: [docs/superpowers/specs/2026-10-30-hkg-package-manager-design.md](docs/superpowers/specs/2026-10-30-hkg-package-manager-design.md)
- Plan: [docs/superpowers/plans/2026-10-30-phase-1-package-manager.md](docs/superpowers/plans/2026-10-30-phase-1-package-manager.md)
- On-car checklist: [docs/testing/2026-10-30-phase-1.md](docs/testing/2026-10-30-phase-1.md)
- Phase 2 on-car checklist: [docs/testing/2026-10-31-phase-2.md](docs/testing/2026-10-31-phase-2.md)
- Catalog format: [docs/catalog/format.md](docs/catalog/format.md)
- Mirror CLI (copies a signed catalog to a stick): [cli/README.md](cli/README.md)
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

**Back up `private/keys/hkgpm.jks` and its password file.** NEMESIS updates a package by
uninstalling it first, so a lost key doesn't block updates; but keep the same key so every
version you hand out is signed the same way and a plain `pm install -r` stays possible.

Full test run:

    tools/test-java.sh && sh tests/kit/run.sh && sh tests/resume/run.sh && sh tests/app/check-app.sh && sh tests/package/run.sh

Python tests (stdlib plus `openssl`):

    python3 -m unittest discover -s tests/catalog && python3 -m unittest discover -s tests/cli

Shell lint (head unit shell has no `printf`, `tail`, `head`, `seq`, `awk`, `grep`, `[[ ]]`):
`tools/lint-sh.sh kit package stick`.

## Install

Copy the contents of `dist/hkg-package-manager.zip` (`NEMESIS.data` and `packages/`) to the root of
a USB stick and plug it into the car. NEMESIS installs it. It needs NEMESIS with its command
channel running. Then follow the checklist.

## Licence

GPL-3.0 (see `LICENSE`). Credits: `NOTICE.md`.
