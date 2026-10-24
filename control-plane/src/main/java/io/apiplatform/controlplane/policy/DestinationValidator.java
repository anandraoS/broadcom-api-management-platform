package io.apiplatform.controlplane.policy;

import java.net.InetAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * SSRF defence for backend targets: scheme + host allowlist, no credentials in the URL, and a block list of
 * address ranges that must never be proxied to (link-local / cloud metadata, any-local, multicast).
 * Loopback is permitted only when explicitly enabled (local demo).
 */
@Component
public class DestinationValidator {
    private final DestinationProperties props;

    public DestinationValidator(DestinationProperties props) {
        this.props = props;
    }

    public List<String> validate(String url) {
        List<String> errors = new ArrayList<>();
        URI uri;
        try {
            uri = URI.create(url);
        } catch (Exception e) {
            return List.of("target url is not a valid URI");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) errors.add("target scheme must be http or https");
        if (uri.getUserInfo() != null) errors.add("credentials in target url are not allowed");
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            errors.add("target host is required");
            return errors;
        }
        host = host.toLowerCase(Locale.ROOT);
        if (!hostAllowed(host)) errors.add("target host is not in the destination allowlist: " + host);
        if (uri.getPort() != -1 && (uri.getPort() < 1 || uri.getPort() > 65535)) errors.add("invalid target port");
        if (uri.getQuery() != null || uri.getFragment() != null) errors.add("target url must not contain query or fragment");
        try {
            for (InetAddress a : InetAddress.getAllByName(host)) {
                if (a.isAnyLocalAddress() || a.isLinkLocalAddress() || a.isMulticastAddress()) {
                    errors.add("target resolves to a blocked address range: " + a.getHostAddress());
                } else if (a.isLoopbackAddress() && !props.allowLoopback()) {
                    errors.add("loopback targets are disabled");
                }
            }
        } catch (Exception e) {
            errors.add("target host cannot be resolved");
        }
        return errors;
    }

    boolean hostAllowed(String host) {
        for (String entry : props.allowedHosts()) {
            String e = entry.toLowerCase(Locale.ROOT);
            if (e.startsWith("*.") ? host.endsWith(e.substring(1)) : host.equals(e)) return true;
        }
        return false;
    }
}
