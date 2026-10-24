# ADR-0006: Resilience primitives: end-to-end timeout budget, safe-only retries, consecutive-failure breaker, semaphore bulkhead

* Status: accepted - Date: 2026-10-17

## Decision
* **One timeout budget** per request (`policy.timeoutMs`) covering all attempts - callers get a bounded worst-case latency and retries cannot multiply it.
* **Retries** only for GET/HEAD/OPTIONS when the policy enables them, on connect errors and 502/503/504, <= 3 attempts, full-jitter exponential backoff, only within the remaining budget.
* **Circuit breaker** per target: opens after 5 consecutive failures for 10 s, one half-open probe. Failure = connect error, timeout, 502/503/504.
* **Bulkhead:** per-route semaphore with immediate rejection (503 + Retry-After) instead of queueing.
* **Active health checks** remove targets from rotation within seconds.

## Alternatives
1. Resilience4j (rate-based breaker, bulkhead, retry): vetted, richer metrics; adds dependency/config surface and its default retry is per-attempt timeout. Reasonable upgrade path.
2. Per-attempt timeouts: simpler but worst-case latency = attempts x timeout.
3. Retrying POST with idempotency keys: safe only if the backend honours the key; unknown for arbitrary backends -> never by default.
4. Queue-based backpressure: hides overload, grows memory and latency.

## Consequences
+ Predictable tail latency and bounded retry amplification; tiny, fully tested code with injectable clocks.
- Consecutive-failure breakers react slowly to partial failure rates (e.g. 40% errors never open if successes interleave); no retry budget across requests; bulkhead is per route per instance (not global).

## Review triggers
Partial-failure incidents the breaker misses; need for per-target latency-based ejection (outlier detection); adopting a service mesh that provides these natively.
