package io.apiplatform.gateway.config;

import io.apiplatform.gateway.GatewayProperties;
import java.net.InetAddress;
import java.net.URI;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Data-plane SSRF defence, independent of the control plane (defence in depth): the gateway refuses to route to a
 * host outside its own allowlist or to link-local/any-local/multicast addresses, even if a snapshot says otherwise.
 * Checked when a snapshot is applied and again (resolving DNS) before each proxied request to limit DNS rebinding.
 */
@Component
public class DestinationGuard {
    private final GatewayProperties props;

    public DestinationGuard(GatewayProperties props) {
        this.props = props;
    }

    public boolean hostAllowed(String url) {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (Exception e) {
            return false;
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) return false;
        if (uri.getUserInfo() != null || uri.getHost() == null) return false;
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        for (String entry : props.allowedHosts()) {
            String e = entry.toLowerCase(Locale.ROOT);
            if (e.startsWith("*.") ? host.endsWith(e.substring(1)) : host.equals(e)) return true;
        }
        return false;
    }

    /** Resolves the host and rejects blocked address ranges. Returns false on resolution failure. */
    public boolean addressesSafe(String url) {
        try {
            for (InetAddress a : InetAddress.getAllByName(URI.create(url).getHost())) {
                if (a.isAnyLocalAddress() || a.isLinkLocalAddress() || a.isMulticastAddress()) return false;
                if (a.isLoopbackAddress() && !props.allowLoopback()) return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean allowed(String url) {
        return hostAllowed(url) && addressesSafe(url);
    }
}
