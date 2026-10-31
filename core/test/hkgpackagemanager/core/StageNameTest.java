package hkgpackagemanager.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class StageNameTest {
    @Test public void flattensSeparators() { assertEquals("HKGPM_queue.json", StageName.flatten("HKGPM/queue.json")); }
    @Test public void replacesOddCharacters() { assertEquals("a_b__c", StageName.flatten("a b'/c")); }
    @Test public void neverDotNames() {
        assertEquals("_..", StageName.flatten(".."));
        assertEquals("_", StageName.flatten(""));
    }
}
