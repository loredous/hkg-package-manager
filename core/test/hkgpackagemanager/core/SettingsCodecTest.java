package hkgpackagemanager.core;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import org.junit.Test;

public class SettingsCodecTest {
    @Test public void codec() {
        assertEquals(new Settings(Button.NAVIGATION, 5), SettingsCodec.decode("button=navigation\npresses=9\n"));
        assertEquals(Settings.DEFAULT, SettingsCodec.decode("garbage"));
        assertEquals("button=off\npresses=2\n", SettingsCodec.encode(new Settings(Button.OFF, 2)));
    }

    @Test public void defaultIsHoldMedia() {
        assertEquals(new Settings(Button.MEDIA_HOLD, 3), Settings.DEFAULT);
        assertEquals("button=media_hold\npresses=3\n", SettingsCodec.encode(Settings.DEFAULT));
    }

    @Test public void holdOpensOnOneBroadcastPressesCountOtherwise() {
        assertEquals(1, new Settings(Button.MEDIA_HOLD, 3).broadcastsNeeded());
        assertEquals(1, new Settings(Button.MEDIA_HOLD, 5).broadcastsNeeded());
        assertEquals(3, new Settings(Button.NAVIGATION, 3).broadcastsNeeded());
        assertEquals(5, new Settings(Button.NAVIGATION, 5).broadcastsNeeded());
    }

    @Test public void oldMediaSettingFallsBackToDefaultButton() {
        assertEquals(new Settings(Button.MEDIA_HOLD, 4), SettingsCodec.decode("button=media\npresses=4\n"));
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
        assertEquals(new Settings(Button.MEDIA_HOLD, 2), SettingsCodec.decode("button=bogus\npresses=1\n"));
        assertEquals(new Settings(Button.OFF, 3), SettingsCodec.decode("button=off\npresses=abc\n"));
        assertEquals(Settings.DEFAULT, SettingsCodec.decode(null));
        assertEquals(Settings.DEFAULT, SettingsCodec.decode(""));
        assertEquals(new Settings(Button.NAVIGATION, 3), SettingsCodec.decode("button=navigation\n"));
    }

    @Test public void actionsAreTheCapturedOnes() {
        assertArrayEquals(new String[] {"com.mobis.intent.action.MEDIAGATE_SHORTCUT"}, Button.MEDIA_HOLD.actions);
        assertEquals(0, Button.OFF.actions.length);
        assertArrayEquals(new String[] {"com.hkmc.intent.action.NAVI_SHORTCUT"}, Button.NAVIGATION.actions);
        assertTrue(Button.MEDIA_HOLD.hold);
        assertFalse(Button.NAVIGATION.hold);
        assertFalse(Button.OFF.hold);
    }

    @Test public void forActionFindsMediaAndNavigation() {
        assertEquals(Button.NAVIGATION, Button.forAction("com.hkmc.intent.action.NAVI_SHORTCUT"));
        assertEquals(Button.MEDIA_HOLD, Button.forAction("com.mobis.intent.action.MEDIAGATE_SHORTCUT"));
        // a short Media press switches source (GPM_SHORTCUT and others): never a trigger
        assertNull(Button.forAction("com.hkmc.intent.action.GPM_SHORTCUT"));
    }

    @Test public void forActionUnknownIsNull() {
        assertNull(Button.forAction("nope"));
        assertNull(Button.forAction(null));
    }

    @Test public void allActionsIsEveryActionInEnumOrder() {
        String[] all = Button.allActions();
        assertEquals(2, all.length);
        assertEquals(all.length, new java.util.HashSet<String>(Arrays.asList(all)).size());
        assertEquals("com.hkmc.intent.action.NAVI_SHORTCUT", all[0]);
        assertEquals("com.mobis.intent.action.MEDIAGATE_SHORTCUT", all[1]);
    }
}
