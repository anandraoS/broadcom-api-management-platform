package io.apiplatform.controlplane.service;

import io.apiplatform.controlplane.policy.PolicyConfig;
import java.time.Instant;
import java.util.List;

/** Immutable configuration document served to gateways (the data-plane contract). */
public record GatewaySnapshot(String version, Instant generatedAt, List<RouteEntry> routes, List<KeyEntry> keys) {
    public record RouteEntry(String apiSlug, String versionLabel, String basePath, List<TargetEntry> targets,
                             PolicyConfig policy, int policyVersion) {}

    public record TargetEntry(String url, int weight, String healthPath) {}

    public record KeyEntry(String prefix, String hash, Instant expiresAt, String clientId, List<String> apiSlugs) {}
}
