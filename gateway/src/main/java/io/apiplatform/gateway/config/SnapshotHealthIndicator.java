package io.apiplatform.gateway.config;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Readiness input. A gateway with no configuration cannot route anything, so it reports OUT_OF_SERVICE until a
 * snapshot (from the control plane or the on-disk cache) has been applied. After that it stays UP even if the control
 * plane later becomes unreachable: serving the last-known-good config is the intended degraded mode.
 */
@Component("snapshot")
public class SnapshotHealthIndicator implements HealthIndicator {
    private final SnapshotHolder holder;

    public SnapshotHealthIndicator(SnapshotHolder holder) {
        this.holder = holder;
    }

    @Override
    public Health health() {
        if (!holder.everLoaded()) return Health.outOfService().withDetail("reason", "no configuration loaded yet").build();
        var s = holder.current();
        return Health.up().withDetail("version", s.snapshot().version()).withDetail("routes", s.routes().size())
                .withDetail("appliedAt", s.appliedAt().toString()).build();
    }
}
