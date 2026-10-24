package io.apiplatform.gateway;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * redisFailMode: LOCAL = fall back to per-instance limiting (availability first, limits become approximate);
 * CLOSED = reject with 503 while Redis is down (strictness first). See docs/rate-limiting.md.
 */
@ConfigurationProperties(prefix = "gateway")
public record GatewayProperties(
        String instanceId,
        String controlPlaneUrl,
        String serviceToken,
        long snapshotPollMs,
        String jwtSecret,
        String jwtIssuer,
        List<String> allowedHosts,
        boolean allowLoopback,
        boolean redisEnabled,
        String redisFailMode,
        int redisFallbackReplicas,
        int maxConcurrentPerRoute,
        long maxResponseBytes,
        int maxRetryAttempts,
        long retryBaseDelayMs,
        int breakerFailureThreshold,
        long breakerOpenMs,
        long healthCheckMs,
        long telemetryFlushMs,
        boolean trustForwardedFor,
        String snapshotCacheFile) {

    public GatewayProperties {
        if (instanceId == null || instanceId.isBlank()) instanceId = "gw-local";
        allowedHosts = allowedHosts == null ? List.of() : List.copyOf(allowedHosts);
        if (redisFailMode == null || redisFailMode.isBlank()) redisFailMode = "LOCAL";
        if (redisFallbackReplicas < 1) redisFallbackReplicas = 1;
        if (maxConcurrentPerRoute < 1) maxConcurrentPerRoute = 200;
        if (maxResponseBytes < 1) maxResponseBytes = 10L * 1024 * 1024;
        if (maxRetryAttempts < 1) maxRetryAttempts = 3;
        if (retryBaseDelayMs < 1) retryBaseDelayMs = 50;
        if (breakerFailureThreshold < 1) breakerFailureThreshold = 5;
        if (breakerOpenMs < 1) breakerOpenMs = 10_000;
        if (snapshotPollMs < 100) snapshotPollMs = 5_000;
        if (healthCheckMs < 100) healthCheckMs = 5_000;
        if (telemetryFlushMs < 100) telemetryFlushMs = 15_000;
    }
}
