package io.apiplatform.controlplane.policy;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Allowlist for backend destinations. In production, list explicit internal service hostnames only and keep
 * allowPrivate=false unless the allowlisted hostnames resolve to private cluster addresses by design.
 */
@ConfigurationProperties(prefix = "app.destinations")
public record DestinationProperties(List<String> allowedHosts, boolean allowLoopback) {
    public DestinationProperties {
        allowedHosts = allowedHosts == null ? List.of() : List.copyOf(allowedHosts);
    }
}
