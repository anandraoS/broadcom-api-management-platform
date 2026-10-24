package io.apiplatform.controlplane.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * jwtSecret: HS256 key for the local development identity mode only. In production configure
 * spring.security.oauth2.resourceserver.jwt.issuer-uri (OIDC) and remove jwtSecret; see docs/security.md.
 * gatewayToken: shared service credential for the gateway -> control-plane channel (use mTLS or workload
 * identity in production).
 */
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(String jwtSecret, String issuer, List<String> corsOrigins, String gatewayToken) {
    public SecurityProperties {
        corsOrigins = corsOrigins == null ? List.of() : List.copyOf(corsOrigins);
        if (issuer == null || issuer.isBlank()) issuer = "api-platform-dev";
    }
}
