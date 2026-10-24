package io.apiplatform.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import io.apiplatform.gateway.ratelimit.InMemoryRateLimiter;
import io.apiplatform.gateway.ratelimit.RateLimiter.Algorithm;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class InMemoryRateLimiterTest {
    private final AtomicLong now = new AtomicLong(60_000L * 1000); // aligned to a window start
    private final InMemoryRateLimiter limiter = new InMemoryRateLimiter(now::get);

    @Test
    void fixedWindowAllowsLimitThenRejectsUntilNextWindow() {
        for (int i = 0; i < 5; i++) assertThat(limiter.check("k", 5, 5, Algorithm.FIXED_WINDOW).allowed()).isTrue();
        var rejected = limiter.check("k", 5, 5, Algorithm.FIXED_WINDOW);
        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.remaining()).isZero();
        assertThat(rejected.retryAfterSeconds()).isBetween(1L, 60L);
        now.addAndGet(60_000);
        assertThat(limiter.check("k", 5, 5, Algorithm.FIXED_WINDOW).allowed()).isTrue();
    }

    @Test
    void fixedWindowAllowsBurstAtBoundary_knownWeakness() {
        // 5 requests at the very end of window 1 and 5 at the start of window 2 are all allowed: 10 in ~1 second.
        now.addAndGet(59_999);
        for (int i = 0; i < 5; i++) assertThat(limiter.check("k", 5, 5, Algorithm.FIXED_WINDOW).allowed()).isTrue();
        now.addAndGet(2);
        for (int i = 0; i < 5; i++) assertThat(limiter.check("k", 5, 5, Algorithm.FIXED_WINDOW).allowed()).isTrue();
    }

    @Test
    void slidingWindowRemovesTheBoundaryBurst() {
        now.addAndGet(59_999);
        for (int i = 0; i < 5; i++) assertThat(limiter.check("k", 5, 5, Algorithm.SLIDING_WINDOW).allowed()).isTrue();
        now.addAndGet(2); // new window, but the previous window still weighs ~100%
        int allowedAtBoundary = 0;
        for (int i = 0; i < 5; i++) if (limiter.check("k", 5, 5, Algorithm.SLIDING_WINDOW).allowed()) allowedAtBoundary++;
        // fixed window would admit all 5 here (10 requests in ~1s); the weighted estimate admits at most 1 (epsilon below the limit)
        assertThat(allowedAtBoundary).isLessThanOrEqualTo(1);
        now.addAndGet(30_000); // half a window later the previous contribution has halved
        assertThat(limiter.check("k", 5, 5, Algorithm.SLIDING_WINDOW).allowed()).isTrue();
    }

    @Test
    void tokenBucketAllowsBurstThenRefillsAtSteadyRate() {
        // 60/min = 1 token per second, burst 3
        for (int i = 0; i < 3; i++) assertThat(limiter.check("k", 60, 3, Algorithm.TOKEN_BUCKET).allowed()).isTrue();
        assertThat(limiter.check("k", 60, 3, Algorithm.TOKEN_BUCKET).allowed()).isFalse();
        now.addAndGet(1_000);
        assertThat(limiter.check("k", 60, 3, Algorithm.TOKEN_BUCKET).allowed()).isTrue();
        assertThat(limiter.check("k", 60, 3, Algorithm.TOKEN_BUCKET).allowed()).isFalse();
        now.addAndGet(10_000); // refill is capped at burst
        int allowed = 0;
        for (int i = 0; i < 10; i++) if (limiter.check("k", 60, 3, Algorithm.TOKEN_BUCKET).allowed()) allowed++;
        assertThat(allowed).isEqualTo(3);
    }

    @Test
    void keysAreIndependent() {
        for (int i = 0; i < 3; i++) limiter.check("a", 3, 3, Algorithm.FIXED_WINDOW);
        assertThat(limiter.check("a", 3, 3, Algorithm.FIXED_WINDOW).allowed()).isFalse();
        assertThat(limiter.check("b", 3, 3, Algorithm.FIXED_WINDOW).allowed()).isTrue();
    }

    @Test
    void remainingCountsDown() {
        assertThat(limiter.check("k", 3, 3, Algorithm.FIXED_WINDOW).remaining()).isEqualTo(2);
        assertThat(limiter.check("k", 3, 3, Algorithm.FIXED_WINDOW).remaining()).isEqualTo(1);
    }

    @Test
    void concurrentCallersNeverExceedTheLimit() throws Exception {
        int threads = 64, perThread = 50, limit = 500;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger allowed = new AtomicInteger();
        ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();
        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                try {
                    go.await();
                    for (int i = 0; i < perThread; i++) {
                        if (limiter.check("shared", limit, limit, Algorithm.FIXED_WINDOW).allowed()) allowed.incrementAndGet();
                    }
                } catch (Throwable e) {
                    errors.add(e);
                }
            });
        }
        go.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        assertThat(errors).isEmpty();
        assertThat(allowed.get()).isEqualTo(limit); // 3200 attempts, exactly 500 admitted (no lost updates)
    }

    @Test
    void concurrentTokenBucketNeverExceedsBurstWithoutTimePassing() throws Exception {
        int threads = 32;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicInteger allowed = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(threads);
        for (int t = 0; t < threads; t++) {
            pool.submit(() -> {
                for (int i = 0; i < 100; i++) if (limiter.check("b", 60, 25, Algorithm.TOKEN_BUCKET).allowed()) allowed.incrementAndGet();
                done.countDown();
            });
        }
        done.await();
        pool.shutdown();
        assertThat(allowed.get()).isEqualTo(25);
    }
}
