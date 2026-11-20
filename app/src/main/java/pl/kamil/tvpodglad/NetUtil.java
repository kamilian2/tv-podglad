package pl.kamil.tvpodglad;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;

final class NetUtil {
    private NetUtil() {}

    /** Adres IPv4 telewizora w sieci lokalnej (preferuje eth0, potem wlan0). */
    static String localIp() {
        String fallback = null;
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a instanceof Inet4Address && !a.isLoopbackAddress()) {
                        String name = ni.getName();
                        if (name.startsWith("eth")) return a.getHostAddress();
                        if (fallback == null || name.startsWith("wlan")) fallback = a.getHostAddress();
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return fallback != null ? fallback : "IP_TELEWIZORA";
    }
}
