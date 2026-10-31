package hkgpackagemanager.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** Turns a package's compatibility data and the car's facts into a green, yellow or red verdict. */
public final class CompatCheck {
    private CompatCheck() {}

    /** Verdict for a loose zip, which has no catalog entry. */
    public static final Verdict LOOSE = new Verdict(Colour.YELLOW, "Not from a catalog");

    public static Verdict check(Compat c, CarFacts car, Set<String> installedIds) {
        List<String> clashes = new ArrayList<String>();
        for (String id : c.conflicts) {
            if (installedIds.contains(id) && !clashes.contains(id)) clashes.add(id);
        }
        if (!clashes.isEmpty()) {
            Collections.sort(clashes);
            StringBuilder sb = new StringBuilder("Conflicts with installed ");
            for (int i = 0; i < clashes.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(clashes.get(i));
            }
            return new Verdict(Colour.RED, sb.toString());
        }
        if (anyMatch(c.notOn, car)) return new Verdict(Colour.RED, "Marked as not working on this car");
        if (anyMatch(c.worksOn, car)) return new Verdict(Colour.GREEN, "Works on this car");
        return new Verdict(Colour.YELLOW, c.isEmpty() ? "No compatibility information" : "Not listed for this car");
    }

    private static boolean anyMatch(List<CompatRule> rules, CarFacts car) {
        for (CompatRule r : rules) if (r.matches(car)) return true;
        return false;
    }
}
