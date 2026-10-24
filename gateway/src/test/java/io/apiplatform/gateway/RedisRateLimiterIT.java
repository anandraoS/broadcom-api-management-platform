package io.apiplatform.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import io.apiplatform.gateway.ratelimit.RateLimiter.Algorithm;
import io.apiplatform.gateway.ratelimit.RedisRateLimiter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Verifies the Lua scripts against a real Redis (atomicity under concurrency, all three algorithms).
 * Skipped automatically when Docker is unavailable - in that case these scripts are UNVERIFIED in that environment.
 */
@Testcontainers(disabledWithoutDocker = true)
class RedisRateLimiterIT {
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);

    private RedisRateLimiter limiter() {
        var cf = new LettuceConnectionFactory(new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        cf.afterPropertiesSet();
        var tpl = new StringRedisTemplate(cf);
        tpl.afterPropertiesSet();
        return new RedisRateLimiter(tpl);
    }

    @Test
    void fixedWindowAdmitsExactlyTheLimitAcrossConcurrentCallers() throws Exception {
        var l = limiter();
        AtomicInteger allowed = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(16);
        for (int i = 0; i < 400; i++) pool.submit(() -> { if (l.check("it-fixed", 100, 100, Algorithm.FIXED_WINDOW).allowed()) allowed.incrementAndGet(); });
        pool.shutdown();
        assertThat(pool.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
        assertThat(allowed.get()).isEqualTo(100);
    }

    @Test
    void slidingWindowAndTokenBucketEnforceLimits() {
        var l = limiter();
        int s = 0, b = 0;
        for (int i = 0; i < 30; i++) if (l.check("it-sliding", 10, 10, Algorithm.SLIDING_WINDOW).allowed()) s++;
        for (int i = 0; i < 30; i++) if (l.check("it-bucket", 60, 5, Algorithm.TOKEN_BUCKET).allowed()) b++;
        assertThat(s).isEqualTo(10);
        assertThat(b).isBetween(5, 6); // burst of 5, plus at most one refilled token during the loop
    }
}
