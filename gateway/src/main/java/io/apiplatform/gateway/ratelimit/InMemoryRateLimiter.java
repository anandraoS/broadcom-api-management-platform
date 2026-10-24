package io.apiplatform.gateway.ratelimit;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Per-instance limiter implementing all three algorithms. Each key has its own tiny state object guarded by that
 * object's monitor, so unrelated keys never contend. Accuracy trade-off: with N gateway instances the effective
 * global limit is up to N x limit (each instance counts independently) - use the Redis limiter for global limits.
 */
public class InMemoryRateLimiter implements RateLimiter {
    private static final long WINDOW_MS = 60_000;
    private static final int MAX_KEYS = 200_000;

    private static final class State {
        // fixed / sliding window
        long windowStart; long current; long previous;
        // token bucket
        double tokens; long lastRefill; boolean init;
    }

    private final Map<String, State> states = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    public InMemoryRateLimiter() { this(System::currentTimeMillis); }

    public InMemoryRateLimiter(LongSupplier clock) { this.clock = clock; }

    @Override
    public Decision check(String key, int limit, int burst, Algorithm algorithm) {
        if (states.size() > MAX_KEYS) evictIdle();
        State s = states.computeIfAbsent(algorithm + "|" + key, k -> new State());
        long now = clock.getAsLong();
        synchronized (s) {
            return switch (algorithm) {
                case FIXED_WINDOW -> fixed(s, now, limit);
                case SLIDING_WINDOW -> sliding(s, now, limit);
                case TOKEN_BUCKET -> bucket(s, now, limit, burst);
            };
        }
    }

    private Decision fixed(State s, long now, int limit) {
        long ws = now - (now % WINDOW_MS);
        if (ws != s.windowStart) { s.windowStart = ws; s.current = 0; }
        long reset = (ws + WINDOW_MS - now + 999) / 1000;
        if (s.current >= limit) return new Decision(false, limit, 0, reset, false, false);
        s.current++;
        return new Decision(true, limit, limit - s.current, reset, false, false);
    }

    /** Sliding-window counter: weighted blend of the previous and current fixed windows (no per-request log). */
    private Decision sliding(State s, long now, int limit) {
        long ws = now - (now % WINDOW_MS);
        if (ws != s.windowStart) {
            s.previous = (ws - s.windowStart == WINDOW_MS) ? s.current : 0;
            s.current = 0;
            s.windowStart = ws;
        }
        double weight = 1.0 - (double) (now - ws) / WINDOW_MS;
        double estimated = s.previous * weight + s.current;
        long reset = (ws + WINDOW_MS - now + 999) / 1000;
        if (estimated >= limit) return new Decision(false, limit, 0, reset, false, false);
        s.current++;
        return new Decision(true, limit, (long) Math.max(0, Math.floor(limit - estimated - 1)), reset, false, false);
    }

    private Decision bucket(State s, long now, int limit, int burst) {
        double ratePerMs = limit / (double) WINDOW_MS;
        if (!s.init) { s.tokens = burst; s.lastRefill = now; s.init = true; }
        s.tokens = Math.min(burst, s.tokens + (now - s.lastRefill) * ratePerMs);
        s.lastRefill = now;
        if (s.tokens >= 1) {
            s.tokens -= 1;
            long toFull = (long) Math.ceil((burst - s.tokens) / ratePerMs / 1000);
            return new Decision(true, limit, (long) Math.floor(s.tokens), toFull, false, false);
        }
        long wait = (long) Math.ceil((1 - s.tokens) / ratePerMs / 1000);
        return new Decision(false, limit, 0, wait, false, false);
    }

    private void evictIdle() {
        long now = clock.getAsLong();
        states.entrySet().removeIf(e -> {
            State s = e.getValue();
            synchronized (s) {
                return now - Math.max(s.windowStart, s.lastRefill) > 2 * WINDOW_MS;
            }
        });
    }

    public int trackedKeys() { return states.size(); }
}
