package io.apiplatform.gateway.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/** Gateway-side mirror of the control plane's snapshot contract (docs/api-contracts.md). Unknown fields are ignored. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Snapshot(String version, Instant generatedAt, List<Route> routes, List<Key> keys) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Route(String apiSlug, String versionLabel, String basePath, List<Target> targets, Policy policy, int policyVersion) {
        /** Low-cardinality metric label (bounded by the number of registered API versions). */
        public String id() { return apiSlug + "/" + versionLabel; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Target(String url, int weight, String healthPath) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Policy(String auth, String requiredRole, Set<String> allowedMethods, Integer rateLimitPerMinute,
                         Integer burst, String rateAlgorithm, Integer timeoutMs, Long maxRequestBytes,
                         List<String> ipAllowCidrs, Boolean retryEnabled) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Key(String prefix, String hash, Instant expiresAt, String clientId, List<String> apiSlugs) {}
}
