package hkgpackagemanager.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PressCounterTest {
    @Test public void threePressesWithinWindow() {
        PressCounter c = new PressCounter(3);
        assertFalse(c.press(0)); assertFalse(c.press(500)); assertTrue(c.press(1000)); assertFalse(c.press(1400));
    }

    @Test public void slowPressRestarts() {
        PressCounter c = new PressCounter(3);
        c.press(0); c.press(500);
        assertFalse(c.press(2100)); assertFalse(c.press(2600)); assertTrue(c.press(3000));
    }

    @Test public void repeatWithin200msIgnored() {
        PressCounter c = new PressCounter(2);
        assertFalse(c.press(0)); assertFalse(c.press(150)); assertTrue(c.press(400));
    }

    @Test public void conflictOnlyForNavigation() {
        assertTrue(PressCounter.conflictsWithNemesisManager(Button.NAVIGATION));
        assertFalse(PressCounter.conflictsWithNemesisManager(Button.MEDIA));
        assertFalse(PressCounter.conflictsWithNemesisManager(Button.OFF));
    }
}
