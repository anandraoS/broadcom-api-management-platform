# T-102: Enforce daily quota in the gateway pipeline  *(ILLUSTRATIVE sample task - not executed)*

- **Spec:** `specs/feature-quota-policy.md` (AC 1, 2, 5, 6, 7). **Role:** R3. **Depends on:** T-101 merged, Lua design approved by R2.
- **Context:** `project-context.md`, `architecture-rules.md`, `performance-review.md`, `api-security-review.md`.
- **Inspect first:** `proxy/GatewayFilter.java` (step order), `ratelimit/RedisRateLimiter.java`, `ResilientRateLimiter.java`, `GatewayRoutingIT`.
- **Files you may change:** `gateway/.../ratelimit/*` (new `QuotaLimiter`), `proxy/GatewayFilter.java` (one new step after the rate limiter), gateway tests.
- **Constraints:** quota key from registry ids only; one atomic Lua call; day from Redis `TIME`; fail mode per policy; no new labels from request data; bounded memory in the local fallback.
- **Acceptance:** 1001st call -> 429 `quota_exceeded`, backend untouched; headers present; concurrency test across two limiter instances; Redis-down tests for OPEN and CLOSED.
- **Gates after this task:** R5 independent tests (G5), R6 security review (G6, human approval required: hot path + abuse surface), R7 perf measurement (p95 delta with real Redis).
- **Evidence:** `mvn -pl gateway verify` (Docker available for `RedisQuotaIT`) output summaries; k6 `quota` results if run; list of unverified claims.
