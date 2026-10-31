# hkg.package.manager

A NEMESIS package manager for Hyundai/Kia Gen5W head units. It installs and uninstalls
NEMESIS packages from a USB stick and has an on-screen app to drive it. Design:
`docs/superpowers/specs/2026-10-30-hkg-package-manager-design.md`.

## Build

(Filled in as tasks land.)

- Key-capture kit (read-only, runs on the car from a USB stick): `tools/build-kit.sh`
  writes `dist/hkg-keycap-kit.zip`.
- Tests: `sh tests/kit/run.sh`; shell lint: `tools/lint-sh.sh kit`.
