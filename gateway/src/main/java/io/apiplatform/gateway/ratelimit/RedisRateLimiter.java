package io.apiplatform.gateway.ratelimit;

import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * Distributed limiter. Each decision is ONE atomic Lua script execution (read-modify-write cannot interleave across
 * gateway instances) and uses Redis TIME, so gateway clock skew cannot distort windows. Keys carry a {hash-tag} so the
 * derived keys of one limiter stay in the same Redis Cluster slot.
 * Not verified against a live Redis in this repository's default test run (no Docker available) - see RedisRateLimiterIT.
 */
public class RedisRateLimiter implements RateLimiter {
    static final String FIXED = """
            local c = redis.call('INCR', KEYS[1])
            if c == 1 then redis.call('EXPIRE', KEYS[1], tonumber(ARGV[2])) end
            local ttl = redis.call('TTL', KEYS[1])
            local limit = tonumber(ARGV[1])
            if c > limit then return {0, 0, ttl} end
            return {1, limit - c, ttl}
            """;

    static final String SLIDING = """
            local t = redis.call('TIME')
            local now = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)
            local win = 60000
            local idx = math.floor(now / win)
            local cur = KEYS[1] .. ':' .. idx
            local prev = KEYS[1] .. ':' .. (idx - 1)
            local limit = tonumber(ARGV[1])
            local p = tonumber(redis.call('GET', prev) or '0')
            local c = tonumber(redis.call('GET', cur) or '0')
            local w = 1 - ((now % win) / win)
            local est = p * w + c
            local reset = math.ceil((win - (now % win)) / 1000)
            if est >= limit then return {0, 0, reset} end
            redis.call('INCR', cur)
            redis.call('PEXPIRE', cur, win * 2)
            return {1, math.max(0, math.floor(limit - est - 1)), reset}
            """;

    static final String BUCKET = """
            local t = redis.call('TIME')
            local now = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)
            local rate = tonumber(ARGV[1]) / 60000
            local burst = tonumber(ARGV[2])
            local h = redis.call('HMGET', KEYS[1], 'tokens', 'ts')
            local tokens = tonumber(h[1])
            local ts = tonumber(h[2])
            if tokens == nil then tokens = burst; ts = now end
            tokens = math.min(burst, tokens + (now - ts) * rate)
            local allowed = 0
            if tokens >= 1 then tokens = tokens - 1; allowed = 1 end
            redis.call('HSET', KEYS[1], 'tokens', tostring(tokens), 'ts', tostring(now))
            redis.call('PEXPIRE', KEYS[1], math.ceil(burst / rate) + 1000)
            local reset
            if allowed == 1 then reset = math.ceil((burst - tokens) / rate / 1000) else reset = math.ceil((1 - tokens) / rate / 1000) end
            return {allowed, math.floor(tokens), reset}
            """;

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<List> fixed = script(FIXED);
    private final DefaultRedisScript<List> sliding = script(SLIDING);
    private final DefaultRedisScript<List> bucket = script(BUCKET);

    public RedisRateLimiter(StringRedisTemplate redis) { this.redis = redis; }

    @SuppressWarnings("rawtypes")
    private static DefaultRedisScript<List> script(String lua) {
        DefaultRedisScript<List> s = new DefaultRedisScript<>(lua, List.class);
        return s;
    }

    @Override
    public Decision check(String key, int limit, int burst, Algorithm algorithm) {
        String k = "rl:{" + key + "}";
        List<?> r = switch (algorithm) {
            case FIXED_WINDOW -> redis.execute(fixed, List.of(k), String.valueOf(limit), "60");
            case SLIDING_WINDOW -> redis.execute(sliding, List.of(k), String.valueOf(limit));
            case TOKEN_BUCKET -> redis.execute(bucket, List.of(k), String.valueOf(limit), String.valueOf(burst));
        };
        if (r == null || r.size() < 3) throw new IllegalStateException("unexpected redis script result");
        return new Decision(((Number) r.get(0)).longValue() == 1, limit, ((Number) r.get(1)).longValue(),
                ((Number) r.get(2)).longValue(), false, false);
    }
}
