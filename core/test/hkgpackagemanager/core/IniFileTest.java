package hkgpackagemanager.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class IniFileTest {
    @Test public void parsesLikeNemesis() {
        IniFile ini = IniFile.parse("# c\n; c\n[nemesis]\r\n  id = my.tool \nname=A=B\n\n[app.0]\nkind=apk\n");
        assertEquals("my.tool", ini.get("nemesis", "id"));
        assertEquals("A=B", ini.get("nemesis", "name"));       // split on first '='
        assertEquals("apk", ini.get("app.0", "kind"));
        assertNull(ini.get("nemesis", "kind"));
        assertTrue(ini.hasSection("app.0"));
    }

    @Test public void firstDuplicateWins() {
        assertEquals("1", IniFile.parse("[n]\nk=1\nk=2\n").get("n", "k"));
    }

    @Test public void ignoresLinesWithoutEqualsAndUnclosedSections() {
        IniFile ini = IniFile.parse("[n\nk=1\n[m]\njunk\nk=2\n");
        assertEquals("1", ini.get("", "k"));
        assertEquals("2", ini.get("m", "k"));
    }
}
