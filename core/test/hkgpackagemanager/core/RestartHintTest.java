package hkgpackagemanager.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class RestartHintTest {
    @Test public void restartHint() {
        assertTrue(RestartHint.mentionsRestart("reboot the head unit to finish"));
        assertTrue(RestartHint.mentionsRestart("Restart now"));
        assertFalse(RestartHint.mentionsRestart("removed hkg.x"));
        assertFalse(RestartHint.mentionsRestart(null));
    }
    @Test public void lastLine() {
        assertEquals("unzip failed", new CliResult(1, "a\nunzip failed\n\n").lastLine());
        assertNull(new CliResult(0, " \n").lastLine());
    }
}
