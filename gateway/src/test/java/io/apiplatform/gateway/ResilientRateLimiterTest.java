package io.apiplatform.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import io.apiplatform.gateway.ratelimit.InMemoryRateLimiter;
import io.apiplatform.gateway.ratelimit.RateLimiter;
import io.apiplatform.gateway.ratelimit.RateLimiter.Algorithm;
import io.apiplatform.gateway.ratelimit.ResilientRateLimiter;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** Failure-behaviour tests for "Redis unavailable" using a fake distributed limiter (no Redis needed). */
class ResilientRateLimiterTest {
    private final AtomicLong now = new AtomicLong(60_000L * 1000);
    private final AtomicInteger primaryCalls = new AtomicInteger();
    private volatile boolean primaryDown;

    private final RateLimiter fakeRedis = (key, limit, burst, alg) -> {
        primaryCalls.incrementAndGet();
        if (primaryDown) throw new IllegalStateException("redis connection refused");
        return new RateLimiter.Decision(true, limit, 99, 10, false, false);
    };

    private ResilientRateLimiter limiter(boolean failClosed, int replicas) {
        return new ResilientRateLimiter(fakeRedis, new InMemoryRateLimiter(now::get), failClosed, replicas, 5_000, now::get, null, null);
    }

    @Test
    void usesDistributedLimiterWhenHealthy() {
        var d = limiter(false, 2).check("k", 100, 10, Algorithm.TOKEN_BUCKET);
        assertThat(d.allowed()).isTrue();
        assertThat(d.remaining()).isEqualTo(99);
        assertThat(d.degraded()).isFalse();
    }

    @Test
    void localModeFallsBackWithLimitDividedByReplicas() {
        var l = limiter(false, 4);
        primaryDown = true;
        int allowed = 0;
        for (int i = 0; i < 100; i++) if (l.check("k", 100, 100, Algorithm.FIXED_WINDOW).allowed()) allowed++;
        assertThat(allowed).isEqualTo(25); // 100 / 4 replicas approximates the global limit
        assertThat(l.check("k", 100, 100, Algorithm.FIXED_WINDOW).degraded()).isTrue();
    }

    @Test
    void closedModeRejectsAsUnavailableInsteadOfAllowingUnlimitedTraffic() {
        var l = limiter(true, 2);
        primaryDown = true;
        var d = l.check("k", 100, 10, Algorithm.TOKEN_BUCKET);
        assertThat(d.allowed()).isFalse();
        assertThat(d.unavailable()).isTrue();
    }

    @Test
    void doesNotHammerRedisDuringBackoffAndRecoversAfterwards() {
        var l = limiter(false, 1);
        primaryDown = true;
        for (int i = 0; i < 50; i++) l.check("k", 1000, 1000, Algorithm.FIXED_WINDOW);
        assertThat(primaryCalls.get()).isEqualTo(1); // one failed attempt, then 49 requests skip the dead dependency
        primaryDown = false;
        now.addAndGet(5_001);
        var d = l.check("k", 1000, 1000, Algorithm.FIXED_WINDOW);
        assertThat(d.degraded()).isFalse();
        assertThat(primaryCalls.get()).isEqualTo(2);
    }

    @Test
    void withoutRedisConfiguredLimitsAreLocalAndNotMarkedDegraded() {
        var l = new ResilientRateLimiter(null, new InMemoryRateLimiter(now::get), false, 3, 5_000, now::get, null, null);
        var d = l.check("k", 10, 10, Algorithm.FIXED_WINDOW);
        assertThat(d.allowed()).isTrue();
        assertThat(d.degraded()).isFalse();
        assertThat(d.limit()).isEqualTo(10);
    }
}
