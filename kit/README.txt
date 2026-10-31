HKG key-capture kit (read-only)

Finds out which broadcast each hardware button on the head unit sends. Changes nothing
on the car.

1. Copy the contents of this zip to an empty FAT or exFAT USB stick.
2. Optional: press the buttons you care about just before inserting the stick; the kit also
   reads what was logged just before insert.
3. Insert the stick and tap Yes when NEMESIS asks to run it.
4. When the toast "Key capture: press each button now (60 seconds)" appears, press each
   candidate button in a known order, short press and long press, and write the order down:
   Media, Navigation, the star / custom key if present, then the steering-wheel keys.
5. Wait for the toast "Key capture finished, unplug the drive", then remove the stick.
6. Keep the KEYCAP folder from the stick private (do not post it) and send or copy it to
   private/KEYCAP/ in the repo.

What is kept: KEYCAP/keys.txt has only log lines about key events and shortcuts
(ModeKeyDispatcher, KEYCODE_, _SHORTCUT, "send key"); KEYCAP/broadcasts.txt has only recent
broadcast lines naming a _SHORTCUT or CUSTOM action; KEYCAP/summary.txt has the product id.
Nothing else from the log is kept, and the temporary files are deleted.
