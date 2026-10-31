package hkgpackagemanager.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A package's compatibility metadata: works_on and not_on rules plus conflicting package ids. */
public final class Compat {
    public static final Compat NONE = new Compat(null, null, null);

    public final List<CompatRule> worksOn;
    public final List<CompatRule> notOn;
    public final List<String> conflicts;

    public Compat(List<CompatRule> worksOn, List<CompatRule> notOn, List<String> conflicts) {
        this.worksOn = copy(worksOn);
        this.notOn = copy(notOn);
        this.conflicts = copy(conflicts);
    }

    private static <T> List<T> copy(List<T> l) {
        if (l == null) return Collections.<T>emptyList();
        return Collections.unmodifiableList(new ArrayList<T>(l));
    }

    public boolean isEmpty() {
        return worksOn.isEmpty() && notOn.isEmpty() && conflicts.isEmpty();
    }
}
