package io.apiplatform.gateway.auth;

import java.net.InetAddress;
import java.util.List;

/** IPv4 CIDR matching. An empty/null list means "no IP restriction". */
public final class IpAllowList {
    private IpAllowList() {}

    public static boolean allowed(List<String> cidrs, String remoteIp) {
        if (cidrs == null || cidrs.isEmpty()) return true;
        long ip;
        try {
            if (remoteIp == null || !remoteIp.matches("[0-9.]+")) return false; // IPv6 or garbage: not allowed under an IPv4 allowlist
            ip = toLong(InetAddress.getByName(remoteIp).getAddress());
        } catch (Exception e) {
            return false;
        }
        for (String cidr : cidrs) {
            try {
                String[] p = cidr.split("/");
                int bits = Integer.parseInt(p[1]);
                long net = toLong(InetAddress.getByName(p[0]).getAddress());
                long mask = bits == 0 ? 0 : (0xFFFFFFFFL << (32 - bits)) & 0xFFFFFFFFL;
                if ((ip & mask) == (net & mask)) return true;
            } catch (Exception ignored) {
                // a malformed CIDR never grants access
            }
        }
        return false;
    }

    private static long toLong(byte[] b) {
        if (b.length != 4) throw new IllegalArgumentException("IPv4 only");
        return ((b[0] & 0xFFL) << 24) | ((b[1] & 0xFFL) << 16) | ((b[2] & 0xFFL) << 8) | (b[3] & 0xFFL);
    }
}
