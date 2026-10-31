package hkgpackagemanager.core;

/** What the app knows about this car for compatibility checks; wifi is null when unknown. */
public final class CarFacts {
    /** Kernel log line printed when the wifi chip fails to power up. */
    public static final String WIFI_FAIL_LINE = "failed to power up DHD generic adapter, max retry reached";

    public final String buildId;
    public final Boolean wifi;

    public CarFacts(String buildId, Boolean wifi) {
        this.buildId = buildId == null ? "" : buildId;
        this.wifi = wifi;
    }

    /** TRUE if a wlan interface exists, FALSE if the kernel log shows the power-up failure, else null. */
    public static Boolean wifiFrom(boolean hasWlan, String kernelLog) {
        if (hasWlan) return Boolean.TRUE;
        if (kernelLog != null && kernelLog.contains(WIFI_FAIL_LINE)) return Boolean.FALSE;
        return null;
    }
}
