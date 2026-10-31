package hkgpackagemanager.core;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import org.junit.Test;

public class SettingsCodecTest {
    @Test public void codec() {
        assertEquals(new Settings(Button.NAVIGATION, 5), SettingsCodec.decode("button=navigation\npresses=9\n"));
        assertEquals(Settings.DEFAULT, SettingsCodec.decode("garbage"));
        assertEquals("button=off\npresses=2\n", SettingsCodec.encode(new Settings(Button.OFF, 2)));
    }

    @Test public void defaultIsMediaThree() {
        assertEquals(new Settings(Button.MEDIA, 3), Settings.DEFAULT);
    }

    @Test public void roundTrip() {
        for (Button b : Button.values()) {
            for (int p = 2; p <= 5; p++) {
                Settings s = new Settings(b, p);
                assertEquals(s, SettingsCodec.decode(SettingsCodec.encode(s)));
            }
        }
    }

    @Test public void badValuesFallBack() {
        assertEquals(new Settings(Button.MEDIA, 2), SettingsCodec.decode("button=bogus\npresses=1\n"));
        assertEquals(new Settings(Button.OFF, 3), SettingsCodec.decode("button=off\npresses=abc\n"));
        assertEquals(Settings.DEFAULT, SettingsCodec.decode(null));
        assertEquals(Settings.DEFAULT, SettingsCodec.decode(""));
        assertEquals(new Settings(Button.NAVIGATION, 3), SettingsCodec.decode("button=navigation\n"));
    }

    @Test public void mediaActionsAreTheProvisionalCandidates() {
        assertArrayEquals(new String[] {
            "com.mobis.intent.action.MEDIAGATE_SHORTCUT",
            "com.hkmc.intent.action.MEDIA_BLUETOOTH_SHORTCUT",
            "com.hkmc.intent.action.MEDIA_MUSIC_SHORTCUT",
            "com.hkmc.intent.action.MEDIA_USB_AUDIO_SHORTCUT",
            "com.hkmc.intent.action.MEDIA_USB_VIDEO_SHORTCUT",
            "com.hkmc.intent.action.MEDIA_AUDIOCD_SHORTCUT",
            "com.hkmc.intent.action.MEDIA_MCONNECT_SHORTCUT"}, Button.MEDIA.actions);
        assertEquals(0, Button.OFF.actions.length);
        assertArrayEquals(new String[] {"com.hkmc.intent.action.NAVI_SHORTCUT"}, Button.NAVIGATION.actions);
    }

    @Test public void forActionFindsMediaAndNavigation() {
        assertEquals(Button.NAVIGATION, Button.forAction("com.hkmc.intent.action.NAVI_SHORTCUT"));
        assertEquals(Button.MEDIA, Button.forAction("com.hkmc.intent.action.MEDIA_MUSIC_SHORTCUT"));
        assertEquals(Button.MEDIA, Button.forAction("com.mobis.intent.action.MEDIAGATE_SHORTCUT"));
    }

    @Test public void forActionUnknownIsNull() {
        assertNull(Button.forAction("nope"));
        assertNull(Button.forAction(null));
    }

    @Test public void allActionsIsEveryActionInEnumOrder() {
        String[] all = Button.allActions();
        assertEquals(8, all.length);
        assertEquals(all.length, new java.util.HashSet<String>(Arrays.asList(all)).size());
        assertEquals("com.hkmc.intent.action.NAVI_SHORTCUT", all[0]);
        assertEquals("com.mobis.intent.action.MEDIAGATE_SHORTCUT", all[1]);
        assertEquals("com.hkmc.intent.action.MEDIA_MCONNECT_SHORTCUT", all[7]);
    }
}
