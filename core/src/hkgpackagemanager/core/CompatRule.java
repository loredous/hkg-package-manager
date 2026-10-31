package hkgpackagemanager.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One compatibility rule: every condition given must hold. A rule with none is invalid. */
public final class CompatRule {
    public final String build;
    public final List<String> hardware;

    public CompatRule(String build, List<String> hardware) {
        this.build = build;
        this.hardware = hardware == null
                ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(hardware));
    }

    public boolean isValid() {
        return build != null || !hardware.isEmpty();
    }

    public boolean matches(CarFacts car) {
        if (!isValid()) return false;
        if (build != null && (car.buildId.length() == 0 || !Glob.matches(build, car.buildId))) return false;
        for (String h : hardware) {
            boolean want;
            if ("wifi".equals(h)) want = true;
            else if ("!wifi".equals(h)) want = false;
            else return false;
            if (car.wifi == null || car.wifi.booleanValue() != want) return false;
        }
        return true;
    }
}
