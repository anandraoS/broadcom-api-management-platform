package io.apiplatform.controlplane.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Development identity provider. Disabled unless explicitly enabled; never enable in production. */
@ConfigurationProperties(prefix = "app.dev-auth")
public record DevAuthProperties(boolean enabled, String password, List<DevUser> users, long tokenTtlSeconds) {
    public record DevUser(String username, List<String> roles, String team) {}

    public DevAuthProperties {
        users = users == null ? List.of() : List.copyOf(users);
        if (tokenTtlSeconds <= 0) tokenTtlSeconds = 3600;
    }
}
