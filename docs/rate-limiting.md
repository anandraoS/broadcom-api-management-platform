# Rate limiting and quotas

Code: `gateway/.../ratelimit/` (`InMemoryRateLimiter`, `RedisRateLimiter`, `ResilientRateLimiter`), applied in `GatewayFilter`
step 7. Policy fields: `rateLimitPerMinute`, `burst`, `rateAlgorithm`. Key = `route (apiSlug/version) + consumer` (anonymous: source IP).

## Algorithms (all implemented and unit-tested)

| Algorithm | Behaviour | Strength | Weakness | Test |
|---|---|---|---|---|
| `FIXED_WINDOW` | Count per aligned 60 s window | Cheapest (1 counter); trivial in Redis | **Boundary burst**: up to 2x limit around a window edge | `fixedWindowAllowsBurstAtBoundary_knownWeakness` |
| `SLIDING_WINDOW` (counter) | `prev * (1 - elapsed) + current` over two windows | Removes the boundary burst, O(1) memory | Approximation (assumes uniform previous window) | `slidingWindowRemovesTheBoundaryBurst` |
| `TOKEN_BUCKET` | Refill `limit/60 s`, capacity `burst` | Smooth steady rate **and** controlled bursts; best default for APIs | Two numbers to tune; stateful (tokens + timestamp) | `tokenBucketAllowsBurstThenRefillsAtSteadyRate` |

A leaky bucket is the same budget enforced as a *queue/shaper* (constant output rate); the token bucket meters instead of delaying,
which suits an API gateway that should reject rather than buffer (buffering consumes threads/memory).

## Responses

`429 rate_limited` with `Retry-After` (seconds), `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`.
Headers are also set on successful responses so well-behaved clients can self-throttle.

## Local vs distributed

| | Local (in-memory) | Distributed (Redis + Lua) |
|---|---|---|
| Accuracy | Per instance: effective limit up to **N x limit** with N gateways | Global (one authoritative counter) |
| Latency | ~nanoseconds | +1 Redis round trip per request (sub-ms in-VPC; measure it) |
| Failure | None | Redis outage/latency becomes a gateway dependency |
| Scale | Perfect | Redis hot keys / single-slot throughput; cluster needs hash tags |
| Use for | Protection against abuse where approximate is fine; fallback | Contractual quotas and global limits |

`RedisRateLimiter` executes **one atomic Lua script per decision** (no read-modify-write race across instances), uses Redis `TIME`
(no gateway clock skew), expires keys, and tags keys `rl:{...}` for cluster slot affinity. Lettuce timeouts are 200 ms
(`spring.data.redis.timeout`) because the default of 60 s would stall request threads on a dead Redis.

## Concurrency

* Local: one small state object per key guarded by its own monitor; independent keys never contend. Test: 64 threads x 50 calls admit **exactly** the limit (`concurrentCallersNeverExceedTheLimit`), token bucket never exceeds burst without time passing.
* Distributed: Lua atomicity. Test (needs Docker): `RedisRateLimiterIT.fixedWindowAdmitsExactlyTheLimitAcrossConcurrentCallers` - **skipped in the default build environment, so the Lua scripts are unverified there**.
* Memory: idle keys are evicted when the table exceeds 200k entries.

## When Redis is unavailable (`ResilientRateLimiter`)

1. First failure marks Redis *down for 5 s* (no per-request timeout storm; test `doesNotHammerRedisDuringBackoffAndRecoversAfterwards`).
2. Policy `redis-fail-mode`:
   * **LOCAL** (default, availability first): per-instance limiter with `limit / EXPECTED_REPLICAS` so the *aggregate* approximates the global limit (`localModeFallsBackWithLimitDividedByReplicas`). Limits become approximate, not absent.
   * **CLOSED** (strictness first): `503 rate_limiter_unavailable` + `Retry-After` until Redis recovers (`closedModeRejectsAsUnavailableInsteadOfAllowingUnlimitedTraffic`). Choose for paid/contractual quotas where over-admission has a cost.
3. Events `RATE_LIMITER_DEGRADED` / `RATE_LIMITER_RECOVERED` appear in the operations view; counter `gateway_ratelimit_redis_failures_total` drives an alert.
4. Redis health is deliberately **excluded from readiness**: a Redis outage must degrade limiting, not remove every gateway from service.

## Quotas

Per-minute limits are implemented. **Daily/monthly quotas** (cumulative budgets with reset calendars and plan tiers) are specified as the
sample feature in `specs/feature-quota-policy.md` and intentionally not implemented, to keep the spec-driven workflow demonstrable.
