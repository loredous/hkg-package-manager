# Key capture: which broadcast each button sends

Status: pending — the owner runs dist/hkg-keycap-kit.zip; values below are provisional.

Build the kit with `tools/build-kit.sh`, run it on the car (see `kit/README.txt`), then fill
this note in. Task 8 (the opening button) reads it.

## Provisional values

Media (candidates found in the stock framework; no plain `MEDIA_SHORTCUT` string exists, so
the key may broadcast a source-specific action; the 200 ms repeat filter absorbs two actions
from one press):

- `com.mobis.intent.action.MEDIAGATE_SHORTCUT`
- `com.hkmc.intent.action.MEDIA_BLUETOOTH_SHORTCUT`
- `com.hkmc.intent.action.MEDIA_MUSIC_SHORTCUT`
- `com.hkmc.intent.action.MEDIA_USB_AUDIO_SHORTCUT`
- `com.hkmc.intent.action.MEDIA_USB_VIDEO_SHORTCUT`
- `com.hkmc.intent.action.MEDIA_AUDIOCD_SHORTCUT`
- `com.hkmc.intent.action.MEDIA_MCONNECT_SHORTCUT`

Navigation: `com.hkmc.intent.action.NAVI_SHORTCUT` (confirmed via NEMESIS Manager).

## How to fill this in

Copy the stick's `KEYCAP/` to `private/KEYCAP/` (never commit it). Read:

- `keys.txt`: `ModeKeyDispatcher` / `KEYCODE_*` lines for each press, and the `send key`
  (MicomD) lines; match them to the press order you wrote down.
- `broadcasts.txt`: the `act=...` lines for `_SHORTCUT` / `CUSTOM` broadcasts.

Record here, with no private log lines (action strings only):

- the exact action string each pressed button broadcast (Media, Navigation, star/custom,
  steering-wheel keys);
- whether each has a `_LONG` variant;
- your press order.

Then trim the Media candidate list to the actions actually seen.
