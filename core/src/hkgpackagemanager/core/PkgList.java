package hkgpackagemanager.core;

import java.util.ArrayList;
import java.util.List;

/** Parses the `id|name|locked|types` lines of `nemesis pkg list`, skipping anything else. */
public final class PkgList {
    private PkgList() {}

    public static List<PkgListEntry> parse(String output) {
        List<PkgListEntry> out = new ArrayList<PkgListEntry>();
        if (output == null) return out;
        for (String raw : output.split("\n", -1)) {
            String line = raw.endsWith("\r") ? raw.substring(0, raw.length() - 1) : raw;
            String[] p = line.split("\\|", -1);
            if (p.length < 4 || p[0].length() == 0) continue;
            StringBuilder name = new StringBuilder(p[1]);
            for (int i = 2; i < p.length - 2; i++) name.append('|').append(p[i]);
            out.add(new PkgListEntry(p[0], name.toString(), p[p.length - 1], "1".equals(p[p.length - 2].trim())));
        }
        return out;
    }
}
