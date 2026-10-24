# Handoff: R3 Java backend -> R6 Security review   Task: T-102   *(ILLUSTRATIVE sample - not an executed run)*

**Goal:** daily quota enforcement in the gateway pipeline is ready for independent security review.
**State:** done (pending review). **Branch/commit:** `feature/quota-enforcement @ <sha>` *(placeholder)*

**What changed:** `gateway/.../ratelimit/QuotaLimiter.java` (new, Lua script, fail modes) - `proxy/GatewayFilter.java` (one step after rate limiting) - `gateway/src/test/.../QuotaLimiterTest.java`, `RedisQuotaIT.java`.

**Decisions made:** quota key is `qt:{apiSlug/version:clientId}:{yyyyMMdd}` built only from registry ids; day computed in Lua from Redis `TIME`; default fail mode OPEN with bounded over-admission documented; no metric labels beyond `route`.

**Contracts touched:** snapshot `policy.dailyQuota`, `quotaFailMode` (additive); error codes `quota_exceeded`, `quota_unavailable`.

**Evidence:**
| Command | Result | Notes |
|---|---|---|
| `mvn -pl gateway test` | *(fill from real run)* | |
| `mvn -pl gateway verify` | *(fill)* | Redis IT requires Docker - state if skipped |

**UNVERIFIED:** behaviour under real Redis failover; performance delta (R7 to measure).
**Please focus review on:** key construction (any request-derived data?), header exposure, abuse via many keys, OPEN-mode over-admission bound, error bodies.
**Next role loads:** `project-context.md`, `api-security-review.md`, `architecture-rules.md`.
**Must not change:** production code (report findings; the author fixes).
**Human approval needed:** yes - hot path + abuse surface (gate G6).
