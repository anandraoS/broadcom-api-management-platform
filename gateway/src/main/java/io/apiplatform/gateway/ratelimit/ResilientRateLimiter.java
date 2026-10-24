package io.apiplatform.gateway.ratelimit;

import io.apiplatform.gateway.telemetry.TelemetryRecorder;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Wraps the distributed limiter with a failure policy. After a Redis error the distributed path is skipped for
 * {@code backoffMs} (no per-request timeout storm) and decisions come from the local limiter:
 *  - LOCAL  : availability first; the limit is divided by the expected replica count to approximate the global limit;
 *  - CLOSED : strictness first; requests are rejected (503) until Redis recovers.
 */
public class ResilientRateLimiter implements RateLimiter {
    private static final Logger log = LoggerFactory.getLogger(ResilientRateLimiter.class);
    private final RateLimiter primary;
    private final InMemoryRateLimiter fallback;
    private final boolean failClosed;
    private final int replicas;
    private final long backoffMs;
    private final LongSupplier clock;
    private final MeterRegistry registry;
    private final TelemetryRecorder telemetry;
    private volatile long downUntil;
    private volatile boolean reportedDown;

    public ResilientRateLimiter(RateLimiter primary, InMemoryRateLimiter fallback, boolean failClosed, int replicas,
                                long backoffMs, LongSupplier clock, MeterRegistry registry, TelemetryRecorder telemetry) {
        this.primary = primary; this.fallback = fallback; this.failClosed = failClosed; this.replicas = Math.max(1, replicas);
        this.backoffMs = backoffMs; this.clock = clock; this.registry = registry; this.telemetry = telemetry;
    }

    @Override
    public Decision check(String key, int limit, int burst, Algorithm algorithm) {
        long now = clock.getAsLong();
        if (primary != null && now >= downUntil) {
            try {
                Decision d = primary.check(key, limit, burst, algorithm);
                if (reportedDown) {
                    reportedDown = false;
                    log.info("distributed rate limiter recovered");
                    if (telemetry != null) telemetry.event("RATE_LIMITER_RECOVERED", "INFO", null, "redis rate limiter recovered", null);
                }
                return d;
            } catch (Exception e) {
                downUntil = now + backoffMs;
                if (registry != null) registry.counter("gateway.ratelimit.redis.failures").increment();
                if (!reportedDown) {
                    reportedDown = true;
                    log.warn("distributed rate limiter unavailable ({}); mode={}", e.getClass().getSimpleName(), failClosed ? "CLOSED" : "LOCAL");
                    if (telemetry != null) telemetry.event("RATE_LIMITER_DEGRADED", "WARN", null,
                            "redis rate limiter unavailable; mode=" + (failClosed ? "CLOSED" : "LOCAL"), null);
                }
            }
        }
        if (primary != null && failClosed) return new Decision(false, limit, 0, 1, true, true);
        int scaled = primary == null ? limit : Math.max(1, limit / replicas);
        int scaledBurst = primary == null ? burst : Math.max(1, burst / replicas);
        Decision d = fallback.check(key, scaled, scaledBurst, algorithm);
        // report the configured (global) limit to the caller, but keep the local remaining/reset values
        return new Decision(d.allowed(), limit, d.remaining(), d.resetSeconds(), false, primary != null);
    }
}
