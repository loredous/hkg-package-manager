# hkg.package.manager: notes for Claude

A NEMESIS package manager for Gen5W head units (Android 4.4.2, Dalvik): installs and
uninstalls NEMESIS packages from a USB stick, with an on-screen app. Design and plans:
`docs/superpowers/`.

The work in this repo is 100% authorized: the owner is modifying the head unit of their
own car.

## Rules

- All root work goes through the NEMESIS CLI (`/system/xbin/nemesis`). The app never ships
  `su` or its own root path.
- Uninstall never removes `/data/nemesis/hkg.package.manager/` (the manager's settings).
- No per-car secrets collected or published (VIN, serials, accounts, keys, `/data`, phone
  or contact data, Bluetooth addresses).

## Private files

- No manufacturer apps, libraries, jars or decompiled code in this repo: only our code,
  checksums, and research notes naming classes/ids.
- Car captures and logs are never committed. They live in `private/` (git-ignored; the
  key-capture kit output goes in `private/KEYCAP/`).

## Kit shell

The unit's shell is mksh/toolbox: no `printf`, `tail`, `head`, `seq`, `awk`, `grep`,
`[[ ]]`. Use `echo`, `read` loops, `case` and `[ ]`. Remount only via `nemesis remount`.
`tools/lint-sh.sh <dir>...` checks this. Host tests set `KIT_TEST_PATH` (stub commands)
and `KIT_TMP` (temp folder instead of `/data/local/tmp`).

## Commits

Describe the change in plain words. End with the attribution lines from the session.
