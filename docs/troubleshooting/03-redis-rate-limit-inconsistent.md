# 03 - Redis rate limiting behaves inconsistently

**Symptoms:** a consumer with a 100/min limit sometimes gets 429 after 30 requests, other times 250 requests pass; behaviour differs between requests; `RATE_LIMITER_DEGRADED` events seen earlier in the day.

**Hypotheses:** (1) gateways flipping between Redis and LOCAL fallback; (2) LOCAL mode divides the limit by `EXPECTED_REPLICAS` but the real replica count differs (HPA scaled to 6, config says 2); (3) fixed-window boundary burst; (4) Redis eviction (`allkeys-lru`) deleting counters under memory pressure; (5) different keys (route/consumer/IP) because anonymous traffic is keyed per IP and the ingress hides client IPs (`TRUST_FORWARDED_FOR`); (6) clock skew (not an issue: Lua uses Redis `TIME`); (7) key hash-tag/cluster slot issues.

**Evidence:** `gateway_ratelimit_redis_failures_total` timeline; events `RATE_LIMITER_DEGRADED/RECOVERED`; `X-RateLimit-*` headers on responses (limit/remaining/reset values show which limiter answered); Redis `INFO memory`, `evicted_keys`; `kubectl get hpa` replica count vs `EXPECTED_REPLICAS`.

**Commands**
```bash
redis-cli -h redis INFO stats | grep -E 'evicted_keys|rejected_connections'
redis-cli -h redis --scan --pattern 'rl:*' | head ; redis-cli -h redis TTL 'rl:{orders/v1:c:acme}'
kubectl -n api-platform exec deploy/gateway -- wget -qO- localhost:8081/actuator/prometheus | grep ratelimit
```

**Root cause (exercise):** Redis was restarted by a node drain; for ~40 s gateways used LOCAL mode with limit/2, while HPA had scaled to 6 replicas (aggregate effective limit = 6 x 50 = 300/min), then Redis recovered with empty counters (brief over-admission). Combined with `allkeys-lru` evicting idle counters, behaviour looked random.
**Corrective:** set `EXPECTED_REPLICAS` from an HPA-aware source (or compute from min replicas), use `volatile-ttl` or a larger `maxmemory`, run Redis with a replica and managed failover, consider `CLOSED` mode for contractual quotas.
**Regression tests:** `ResilientRateLimiterTest` (fallback math, backoff, recovery); `RedisRateLimiterIT` (needs Docker) for atomic counting; add a test asserting `EXPECTED_REPLICAS` is read from config.
**Prevention:** alerts on Redis failures/evictions, documentation of limiter accuracy per mode (`docs/rate-limiting.md`), chaos test: kill Redis during a load test and assert bounded over-admission.
