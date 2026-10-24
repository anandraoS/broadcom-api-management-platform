package io.apiplatform.gateway.ratelimit;

public interface RateLimiter {

    enum Algorithm {
        FIXED_WINDOW, SLIDING_WINDOW, TOKEN_BUCKET;

        public static Algorithm parse(String s) {
            try { return valueOf(s); } catch (Exception e) { return TOKEN_BUCKET; }
        }
    }

    /**
     * @param allowed        request may proceed
     * @param limit          configured per-minute limit
     * @param remaining      remaining tokens/requests (best effort)
     * @param resetSeconds   seconds until the limit fully replenishes / window rolls over
     * @param unavailable    the limiter itself could not decide (fail-closed mode while Redis is down)
     * @param degraded       decision was made by the local fallback instead of the distributed limiter
     */
    record Decision(boolean allowed, int limit, long remaining, long resetSeconds, boolean unavailable, boolean degraded) {
        public long retryAfterSeconds() { return Math.max(1, resetSeconds); }
    }

    Decision check(String key, int limitPerMinute, int burst, Algorithm algorithm);
}
