package hkgpackagemanager.core;

import java.util.ArrayList;
import java.util.List;

/** NEMESIS.data reader, mirroring the daemon's ini.c: sections, key=value, first duplicate wins. */
public final class IniFile {
    private final List<String[]> items = new ArrayList<String[]>(); // {section, key, value}

    private IniFile() {}

    public static IniFile parse(String text) {
        IniFile ini = new IniFile();
        String section = "";
        String[] lines = (text == null ? "" : text).split("\n", -1);
        for (String raw : lines) {
            String line = raw.trim();
            if (line.length() == 0 || line.charAt(0) == '#' || line.charAt(0) == ';') continue;
            if (line.charAt(0) == '[') {
                int end = line.indexOf(']');
                if (end < 0) continue;
                section = line.substring(1, end).trim();
                continue;
            }
            int eq = line.indexOf('=');
            if (eq < 0) continue;
            ini.items.add(new String[] {section, line.substring(0, eq).trim(), line.substring(eq + 1).trim()});
        }
        return ini;
    }

    /** The first value for the key in the section, or null. */
    public String get(String section, String key) {
        for (String[] item : items) {
            if (item[0].equals(section) && item[1].equals(key)) return item[2];
        }
        return null;
    }

    public boolean hasSection(String section) {
        for (String[] item : items) {
            if (item[0].equals(section)) return true;
        }
        return false;
    }
}
