package hkgpackagemanager.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public class CompatCheckTest {
    private static final String BUILD = "NEEV23.USA.0000.V015.012.260507";
    private static final CarFacts CAR = new CarFacts(BUILD, true);
    private static final Set<String> NONE_INSTALLED = Collections.<String>emptySet();

    private static List<CompatRule> rules(CompatRule... r) { return Arrays.asList(r); }
    private static final List<CompatRule> NO_RULES = Collections.<CompatRule>emptyList();
    private static final List<String> NO_IDS = Collections.<String>emptyList();

    private static Verdict check(List<CompatRule> works, List<CompatRule> not, CarFacts car) {
        return CompatCheck.check(new Compat(works, not, NO_IDS), car, NONE_INSTALLED);
    }

    private static void assertVerdict(Colour c, String reason, Verdict v) {
        assertEquals(c, v.colour);
        assertEquals(reason, v.reason);
    }

    @Test public void noDataIsYellow() {
        assertVerdict(Colour.YELLOW, "No compatibility information", CompatCheck.check(Compat.NONE, CAR, NONE_INSTALLED));
        assertTrue(Compat.NONE.isEmpty());
    }

    @Test public void looseLabel() {
        assertVerdict(Colour.YELLOW, "Not from a catalog", CompatCheck.LOOSE);
    }

    @Test public void buildMatchIsGreen() {
        assertVerdict(Colour.GREEN, "Works on this car", check(rules(new CompatRule("NEEV23.*", null)), NO_RULES, CAR));
    }

    @Test public void noMatchEitherWayIsYellowNotListed() {
        assertVerdict(Colour.YELLOW, "Not listed for this car", check(rules(new CompatRule("OTHER*", null)), NO_RULES, CAR));
    }

    @Test public void negativeBuildIsRed() {
        assertVerdict(Colour.RED, "Marked as not working on this car", check(NO_RULES, rules(new CompatRule("NEEV23.*", null)), CAR));
    }

    @Test public void redWinsOverGreen() {
        Verdict v = check(rules(new CompatRule("*", null)), rules(new CompatRule("NEEV23.*", null)), CAR);
        assertVerdict(Colour.RED, "Marked as not working on this car", v);
    }

    @Test public void conflictInstalledIsRed() {
        Compat c = new Compat(rules(new CompatRule("*", null)), NO_RULES, Arrays.asList("c.d", "a.b", "x.y"));
        Set<String> installed = new HashSet<String>(Arrays.asList("c.d", "a.b"));
        assertVerdict(Colour.RED, "Conflicts with installed a.b, c.d", CompatCheck.check(c, CAR, installed));
    }

    @Test public void conflictWordingWinsOverNotOn() {
        Compat c = new Compat(NO_RULES, rules(new CompatRule("*", null)), Arrays.asList("a.b"));
        assertVerdict(Colour.RED, "Conflicts with installed a.b",
                CompatCheck.check(c, CAR, new HashSet<String>(Arrays.asList("a.b"))));
    }

    @Test public void conflictNotInstalledIgnored() {
        Compat c = new Compat(NO_RULES, NO_RULES, Arrays.asList("a.b"));
        assertVerdict(Colour.YELLOW, "Not listed for this car", CompatCheck.check(c, CAR, NONE_INSTALLED));
        assertFalse(c.isEmpty());
    }

    @Test public void ruleNeedsAllConditions() {
        CompatRule r = new CompatRule("NEEV23.*", Arrays.asList("!wifi"));
        assertFalse(r.matches(CAR));
        assertTrue(r.matches(new CarFacts(BUILD, false)));
        assertVerdict(Colour.YELLOW, "Not listed for this car", check(rules(r), NO_RULES, CAR));
    }

    @Test public void ruleWithNoConditionsIgnored() {
        assertFalse(new CompatRule(null, null).isValid());
        assertFalse(new CompatRule(null, NO_IDS).isValid());
        assertVerdict(Colour.YELLOW, "Not listed for this car", check(rules(new CompatRule(null, null)), NO_RULES, CAR));
        assertVerdict(Colour.YELLOW, "Not listed for this car", check(NO_RULES, rules(new CompatRule(null, NO_IDS)), CAR));
    }

    @Test public void unknownHardwareNeverMatches() {
        CompatRule r = new CompatRule(null, Arrays.asList("bluetooth"));
        assertVerdict(Colour.YELLOW, "Not listed for this car", check(rules(r), NO_RULES, CAR));
        assertVerdict(Colour.YELLOW, "Not listed for this car", check(NO_RULES, rules(r), CAR));
    }

    @Test public void unknownWifiNeverMatches() {
        CarFacts car = new CarFacts(BUILD, null);
        CompatRule yes = new CompatRule(null, Arrays.asList("wifi"));
        CompatRule no = new CompatRule(null, Arrays.asList("!wifi"));
        assertVerdict(Colour.YELLOW, "Not listed for this car", check(rules(yes), NO_RULES, car));
        assertVerdict(Colour.YELLOW, "Not listed for this car", check(NO_RULES, rules(no), car));
    }

    @Test public void unknownBuildNeverMatchesBuildRules() {
        CarFacts car = new CarFacts("", true);
        assertVerdict(Colour.YELLOW, "Not listed for this car", check(rules(new CompatRule("*", null)), NO_RULES, car));
    }

    @Test public void hardwareOnlyRuleIsValid() {
        CompatRule r = new CompatRule(null, Arrays.asList("!wifi"));
        assertTrue(r.isValid());
        assertVerdict(Colour.RED, "Marked as not working on this car", check(NO_RULES, rules(r), new CarFacts(BUILD, false)));
    }

    @Test public void orAcrossRules() {
        assertVerdict(Colour.GREEN, "Works on this car",
                check(rules(new CompatRule("OTHER*", null), new CompatRule("NEEV23.*", null)), NO_RULES, CAR));
    }

    @Test public void wifiFromWlanKernelLog() {
        assertEquals(Boolean.TRUE, CarFacts.wifiFrom(true, null));
        assertEquals(Boolean.FALSE, CarFacts.wifiFrom(false, "a\n" + CarFacts.WIFI_FAIL_LINE + "\nb"));
        assertNull(CarFacts.wifiFrom(false, "x"));
        assertNull(CarFacts.wifiFrom(false, null));
    }

    @Test public void valueClassesAreImmutable() {
        List<CompatRule> src = new ArrayList<CompatRule>();
        src.add(new CompatRule("*", null));
        Compat c = new Compat(src, null, null);
        src.clear();
        assertEquals(1, c.worksOn.size());
        assertTrue(c.notOn.isEmpty());
        assertTrue(c.conflicts.isEmpty());
        try { c.worksOn.add(null); throw new AssertionError("mutable"); } catch (UnsupportedOperationException expected) { }
    }
}
