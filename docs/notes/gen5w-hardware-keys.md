# Gen5W head unit: hardware buttons, keycodes and broadcasts

What each physical button on a Gen5W (Hyundai/Kia/Genesis, Android 4.4.2) head unit
sends: the Micom key code, the Android keycode, the key name the head unit uses
internally, and the broadcast it fires (if any). It's meant for anyone writing a NEMESIS
package that reacts to a button.

## Where this came from

- **Firmware:** `NEEV23.USA.0000.V015.012.260507`
- **Vehicle:** 2023 Ioniq 5 AWD Limited (US)
- **Method:** a read-only capture run from a USB stick. It made no changes to the unit.
  It recorded 60 seconds of logcat filtered to the key path (`MicomD`, `ModeKeyDispatcher`,
  `HkmcInputPolicyService`, `ModeManagerService`, `BroadcastQueue`) while the buttons
  were pressed one at a time, plus a `dumpsys` of registered receivers and recent
  broadcast history.
- **Coverage:**
  - Covered: every button on the centre dash panel and the steering wheel, each with a
    short press.
  - Driver's side dash buttons: pressed in the second capture, and none of them reached
    the head unit (see the note below the steering wheel table).
  - Not pressed: the driving controls.
  - Long presses: tested on the dash keys in a second capture. See
    [Long presses](#long-presses-centre-dash-panel).Har

Other trims and firmware versions may differ. If yours does, please add a column or a
second table rather than overwriting this one.

## How a key press travels

```
button → Micom (MCU) → MicomD (input event) → ModeKeyDispatcher
       → foreground view (as a normal KeyEvent)
       → ModeManagerService (for shortcut keys) → startService + ordered broadcast
```

- **Scan code:** the Micom key code and the Linux scan code are the same number. For
  example, Micom `0x0013` is scan code 19.
- **Input device:**
  - `deviceId=3` is the centre panel;
  - `deviceId=5` is the steering wheel;
  - `deviceId=8` is the volume knob's rotary encoder.
- **Broadcasts:** only shortcut keys produce one. Volume, tune, seek, mute and power are
  delivered only as `KeyEvent`s to the foreground app and to system services.
- **Flags and extras:** shortcut broadcasts are ordered, with flags `0x10000010`
  (`FLAG_RECEIVER_FOREGROUND | FLAG_EXCLUDE_STOPPED_PACKAGES`). They carry these extras:
  - `com.hkmc.mode.EXTRA_SHORTCUT_REASON`
  - `com.hkmc.mode.EXTRA_RESTOREMODE_REASON`
  - `com.hkmc.mode.EXTRA_SAME_MODE`
  - `com.hkmc.mode.EXTRA_RUNNING_STATE`
  - `com.hkmc.mode.TIMESTAMP`
- **Long presses:**
  - **Dash keys:** a held key starts auto-repeating (`repeatCount=1`) about 800 ms
    after it goes down. Shortcut keys report the long press at that moment
    (`isLongPressed - true`), and the long action fires then, not on release.
  - **Steering wheel:** the dispatcher starts a separate 500 ms timer on Volume and
    Voice (PTT) ("start long event … after 500 ms"). Long presses on the steering wheel
    haven't been captured yet.

## Centre dash panel

| Button | Micom / scan | Android keycode | Internal key name | Broadcast on short press |
|---|---|---|---|---|
| Volume knob, turn | (encoder) | `KEYCODE_VOLUME_UP_DUMMY` / `KEYCODE_VOLUME_DOWN_DUMMY`, scan 0 | `FRONT_HW_KEY_022` | none |
| Volume knob, push (power) | `0x01` / 1 | `KEYCODE_POWER_AVN` (232) | `FRONT_HW_KEY_001` | none |
| MAP | `0x04` / 4 | `KEYCODE_SHORTCUT_MAP` (260) | `FRONT_HW_KEY_007` | `com.hkmc.intent.action.MAP_SHORTCUT` |
| NAV | `0x05` / 5 | `KEYCODE_SHORTCUT_NAVI` (255) | `FRONT_HW_KEY_019` | `com.hkmc.intent.action.NAVI_SHORTCUT` |
| MEDIA | `0x13` / 19 | `KEYCODE_SHORTCUT_MEDIA` (251) | `FRONT_HW_KEY_003` | A media mode shortcut, which depends on state (see the notes). Seen: `com.hkmc.intent.action.GPM_SHORTCUT` |
| ☆ Custom | `0x14` / 20 | `KEYCODE_SHORTCUT_CUSTOM` (279) | `FRONT_HW_KEY_028` | none logged. The user-set action ran; on this car it was `DISP_SHORTCUT` (display off) |
| Tune paddle, down | `0x18` / 24 | `KEYCODE_TUNE_DOWN` (224) | `FRONT_HW_KEY_023` | none |
| Tune paddle, up | `0x17` / 23 | `KEYCODE_TUNE_UP` (223) | — | none |
| Tune paddle, press in | `0x11` / 17 | `KEYCODE_TUNE_ENTER` (243) | `FRONT_HW_KEY_006` | none |

## Long presses (centre dash panel)

Each key was held for about 2.5–3.5 seconds.

| Button held | Broadcast | Notes |
|---|---|---|
| ☆ Custom | `com.mobis.intent.action.CUSTOM_SHORTCUT` | Fires at about 800 ms. NEMESIS's manager is one of its receivers. |
| MEDIA | `com.mobis.intent.action.MEDIAGATE_SHORTCUT` | Fires at about 800 ms. |
| NAV | `com.hkmc.intent.action.NAVI_SHORTCUT_LONG` | Fires at about 800 ms. `NaviPopupService` handles it. |
| MAP | no long action | It sends the ordinary `MAP_SHORTCUT` when you let go, even after a 3.6 s hold. |
| Tune paddle, press in | none | Also nothing for 3 quick presses in a row. |
| Tune paddle, up / down | none | Held, the key auto-repeats. Also nothing for 3 quick presses in a row. |

`com.hkmc.intent.action.TUNE_ENTER_SHORTCUT` and `TUNE_ENTER_SHORTCUT_LONG` exist as action
names, and NEMESIS's manager listens for both. But this firmware never sent either one, whether
the key was pressed, held or triple-pressed.

## Steering wheel

| Button | Micom / scan | Android keycode | Internal key name | Broadcast on short press |
|---|---|---|---|---|
| Volume down | `0x27` / 39 | `KEYCODE_VOLUME_DOWN` (25) | `STEERING_HW_KEY_007` | none |
| Volume up | `0x26` / 38 | `KEYCODE_VOLUME_UP` (24) | `STEERING_HW_KEY_006` | none |
| Seek down | `0x23` / 35 | `KEYCODE_CHANNEL_DOWN` (167) | `STEERING_HW_KEY_003` | none |
| Seek up | `0x22` / 34 | `KEYCODE_CHANNEL_UP` (166) | `STEERING_HW_KEY_002` | none |
| Mute | `0x25` / 37 | `KEYCODE_MUTE` (91) | `STEERING_HW_KEY_005` | none |
| Voice (PTT) | `0x28` / 40 | `KEYCODE_SHORTCUT_PTT` (264) | `STEERING_HW_KEY_008` (also logs `STEERING_HW_SHORT_KEY_001`) | `com.hkmc.intent.action.VR_PTT_SHORTCUT` |
| MODE | `0x24` / 36 | `KEYCODE_SHORTCUT_MODE` (254) | `STEERING_HW_KEY_004` | The next source's mode shortcut, which depends on state. Seen: `com.hkmc.intent.action.MEDIA_NATURESOUND_SHORTCUT` |
| ☆ Custom | `0x2b` / 43 | `KEYCODE_SHORTCUT_SWRC_CUSTOM` (294) | `STEERING_HW_KEY_043` | The user-set action. On this car it was `com.mobis.intent.action.VOICE_MEMO_SHORTCUT` |
| Call | `0x2a` / 42 | `KEYCODE_CALL` (5) | `STEERING_HW_KEY_009` | `com.hkmc.intent.action.BTCALL_SHORTCUT` |

## Driver's side dash buttons

None of the buttons on the driver's side of the dash sent anything to the head unit. Pressing
them produced no MicomD `send key` line and no `ModeKeyDispatcher` event. Every button the
head unit handles produces both. These buttons are presumably wired to the car's own body and
driving modules rather than to the infotainment unit, so a NEMESIS package can't use them as
triggers.

## Notes for package authors

- **MAP and NAV** are fixed actions, and each had 4 receivers. NEMESIS's manager
  (`com.nemesis.manager`) listens for nearly every shortcut action, these two included,
  and opens only on the button chosen in its own settings, after three presses.
- **MEDIA and MODE are not fixed.** `ModeManagerService` logs them as
  `EVENT_SHORTCUT : Mode … - <MODE> (0x..)` and broadcasts the target mode's action:
  - MEDIA went to `GPM` (0x74). Android Auto's `GalReceiverService` handled it
    (`ACTION_SHORTCUT_GPM`), and the broadcast itself had **no receivers**.
  - MODE went to `NATURE_SOUND` (0x81).

  Expect other actions with a different source active or no phone connected.
- **Broadcasts that aren't key presses:** `GPM_SHORTCUT` also appeared in the broadcast
  history at start-up with `EXTRA_RESTOREMODE_REASON=2`. That is a mode restore, not a
  button. A receiver that counts presses should ignore broadcasts whose
  `EXTRA_RESTOREMODE_REASON` isn't `0`.
- **The ☆ Custom keys run whatever the owner has assigned in Settings.** The dash ☆
  produced no `ModeManagerService` broadcast at all while it was set to display off. You
  can't rely on either ☆ key sending a particular action on someone else's car.
- **Steering-wheel keys during voice recognition:** while a voice session is active,
  steering-wheel keys go to it first (`ModeKeyConstants.PRIORITY_VR`). In this capture,
  the ☆ press right after PTT fired nothing. The next two presses fired
  `VOICE_MEMO_SHORTCUT` (start, then stop recording).
- **Stopped packages:** because of `FLAG_EXCLUDE_STOPPED_PACKAGES`, a manifest receiver
  only gets these broadcasts once the app has been launched at least once since install.
- **Keys with no broadcast:** volume, seek, tune, mute and power can only be read as
  `KeyEvent`s by the foreground activity. A background package can't watch them through
  broadcasts.

## Shortcut actions with registered receivers

These are from `dumpsys` on this firmware. Something on the unit listens for each of
these actions. Not all of them are tied to a physical button on every car.

- `com.hkmc.intent.action.`:
  - **Navigation:** `NAVI_SHORTCUT`, `NAVI_SHORTCUT_LONG`, `ACTION_NAVI_SHORTCUT`,
    `MAP_SHORTCUT`, `POI_SHORTCUT`, `POI_SHORTCUT_LONG`, `POI_SHORTCUT_PASS_VLS`
  - **Phone and voice:** `PHONE_SHORTCUT`, `PHONE_SHORTCUT_LONG`, `BTCALL_SHORTCUT`,
    `BTCALL_SHORTCUT_LONG`, `BTCALLEND_SHORTCUT`, `VR_PTT_SHORTCUT_LONG`
  - **Settings, media and display:** `SETUP_SHORTCUT`, `RADIO_SHORTCUT`,
    `HOTSPOT_SHORTCUT`, `REARQUIET_SHORTCUT`, `DRVM_SHORTCUT`
  - **Custom-key actions:** `GPM_CUSTOM_SHORTCUT`, `DIPO_CUSTOM_SHORTCUT`,
    `CUSTOM_ACTION_BLOCKED`, `SWRC_CUSTOM_ACTION_BLOCKED`
  - **Telematics and emergency:** `SOS_SHORTCUT`, `SOS_SHORTCUT_LONG`,
    `SOS_SHORTCUT_TM_LONG`, `TMUCALL_SHORTCUT`, `TMUCALL_SHORTCUT_LONG`
  - **Bluelink:** `BLUELINKAPP_SHORTCUT`, `BLUELINKAPP_SHORTCUT_LONG`, and the variants
    `_NOT_ACTIVE`, `_POI`, `_POI_IN`, `_RSA_IN`, `_VCC`, `_PASS_PHONE`,
    `_LONG_PASS_PHONE`, `_USING_SOS_NUMBER`
- `com.mobis.intent.action.`:
  - **Voice and privacy:** `VOICE_MEMO_SHORTCUT`, `PRIVACY_MODE_SHORTCUT`
  - **Driving:** `DRIVER_ASSISTANCE_SHORTCUT`, `ADASMENU_SHORTCUT_STATUS_CHANGED`,
    `DRIVERWATCH_SHORTCUT`, `RVC_SHORTCUT`, `EV_SHORTCUT`
  - **Phone:** `CHANGE_HANDSFREE_DEVICE_SHORTCUT`, `MEDIAGATE_SHORTCUT`
  - **Routing:** `REROUTE_SHORTCUT`, `CANCEL_ROUTE_SHORTCUT`, `KAKAO_DEST_SHORTCUT`
  - **Other:** `SWRC_CUSTOM_SHORTCUT`, `CUSTOM_TAB_ENTRY`
- Other: `com.hkmc.ev.PTT_SHORTCUT`, `android.intent.action.POI_SHORTCUT_PASS_VLS`

These actions were seen in this capture but are not in the list above:
- `com.hkmc.intent.action.GPM_SHORTCUT` (MEDIA): broadcast with **0 receivers**.
- `com.hkmc.intent.action.MEDIA_NATURESOUND_SHORTCUT` (MODE): 1 receiver, declared in an
  app's manifest.
- `com.hkmc.intent.action.VR_PTT_SHORTCUT` (Voice): 1 receiver, declared in an app's
  manifest.
- `com.hkmc.intent.action.DISP_SHORTCUT`: seen only as the dash ☆ key's configured
  action, never as a broadcast.
