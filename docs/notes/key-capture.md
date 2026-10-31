# Key capture: which broadcast each button sends

Status: done 2026-10-31. There were two runs of `dist/hkg-keycap-kit.zip` on the owner's car,
with firmware `NEEV23.USA.0000.V015.012.260507`. The raw output stays in `private/KEYCAP*/`
and is never committed. The full button tables, written for sharing, are in
[gen5w-hardware-keys.md](gen5w-hardware-keys.md).

## What the manager uses

| Setting | Broadcast | Opens on |
|---|---|---|
| Hold Media (default) | `com.mobis.intent.action.MEDIAGATE_SHORTCUT` | one broadcast, fired about 0.8 s into the hold |
| Press Navigation | `com.hkmc.intent.action.NAVI_SHORTCUT` | 2-5 short presses (NEMESIS Manager may use it too) |

The receiver ignores a shortcut broadcast whose `com.hkmc.mode.EXTRA_RESTOREMODE_REASON` isn't
0. Those broadcasts restore a mode at boot and aren't button presses.

## Why not the other candidates

- **Media, short press:** it goes to the current media source, so the action changes with
  state. It sent `com.hkmc.intent.action.GPM_SHORTCUT` (the Android Auto source), with no
  broadcast receivers. `GPM_SHORTCUT` was also re-sent at boot with
  `EXTRA_RESTOREMODE_REASON=2`. None of the `MEDIA_*_SHORTCUT` candidates from the
  framework appeared.
- **Tune Enter** (the tune paddle pressed in), the owner's first choice: no broadcast for a
  press, a 3.6 s hold or a triple press. Tune up and down are the same. These keys only go to
  the foreground view and the media source's own service. NEMESIS Manager listens for
  `TUNE_ENTER_SHORTCUT` and `_LONG`, but this firmware never sent either one. A background app
  could only see the key by reading the input device as root, which we chose not to build.
- **Holding ☆ Custom** (`com.mobis.intent.action.CUSTOM_SHORTCUT`) or NAV
  (`com.hkmc.intent.action.NAVI_SHORTCUT_LONG`): both work, and they could be added as
  Settings options later. The owner picked Media.
- **MAP:** there's no long-press action. A press sends the ordinary `MAP_SHORTCUT` when you
  let go.
- **Driver's side dash buttons:** nothing reaches the head unit.
- **Steering wheel ☆ and the Voice key:** each already has a job (voice memo and voice
  recognition).

## Press order (as the owner checked it)

- **Run 1:**
  1. Dash panel: volume knob (turned, then pushed), MAP, NAV, MEDIA, ☆, tune paddle down,
     tune paddle up, tune paddle pressed in, ☆.
  2. Steering wheel: volume down, volume up, seek down, seek up, mute, Voice, MODE, ☆, Call,
     ☆, ☆.
- **Run 2:**
  1. Tune Enter: held, then triple-pressed.
  2. Tune paddle down and tune paddle up: each held, then triple-pressed.
  3. Holds of ☆, MEDIA, NAV and MAP.
  4. Driver's side dash buttons, which sent nothing.
