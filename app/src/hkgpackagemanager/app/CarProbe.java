package hkgpackagemanager.app;

import hkgpackagemanager.core.CarFacts;
import hkgpackagemanager.core.CliResult;
import hkgpackagemanager.core.Nemesis;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;

/** Reads the car's build id and Wi-Fi state once per process. */
public final class CarProbe {
    private static CarFacts cached;

    private CarProbe() {}

    public static synchronized CarFacts facts(Nemesis n) {
        if (cached == null) {
            boolean hasWlan = hasWlan();
            String log = null;
            if (!hasWlan) {
                CliResult r = n.root("dmesg");
                if (r.exit == 0) log = r.output;
            }
            cached = new CarFacts(buildId(), CarFacts.wifiFrom(hasWlan, log));
        }
        return cached;
    }

    private static boolean hasWlan() {
        String[] names = new File("/sys/class/net").list();
        if (names == null) return false;
        for (String name : names) {
            if (name.startsWith("wlan")) return true;
        }
        return false;
    }

    private static String buildId() {
        Process p = null;
        try {
            p = Runtime.getRuntime().exec(new String[] {"/system/bin/getprop", "ro.build.product"});
            InputStream in = p.getInputStream();
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[256];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                p.waitFor();
                return new String(out.toByteArray(), "UTF-8").trim();
            } finally {
                in.close();
                p.getOutputStream().close();
                p.getErrorStream().close();
            }
        } catch (Exception e) {
            return "";
        } finally {
            if (p != null) p.destroy();
        }
    }
}
