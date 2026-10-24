# Feature: daily request quota policy (new gateway policy type)

Status: **draft - forward-looking sample, NOT implemented.** Owner: platform team. Reviewers: security, gateway, DBA. Approvals required from a human for phases 1-3, security review and release.

## 1. Business objective
Partners are contracted for a daily call budget (e.g. 50,000/day on the free plan). Today only per-minute limits exist, so a partner can consume the whole monthly allowance in an hour or be throttled unfairly. Objective: enforce **cumulative daily quotas per consumer per API** and expose usage. Success metrics: 100% of subscribed plans enforceable; over-admission < 1% of quota under normal operation; no measurable p95 regression beyond +2 ms per request.

## 2. User stories
* As an **API owner** I want to attach a daily quota to an API policy so that partners cannot exceed their contract.
* As a **consumer developer** I want `X-Quota-Limit/Remaining/Reset` headers and a clear `429 quota_exceeded` so I can plan usage.
* As an **operator** I want quota exhaustion and degraded-mode events in the incident view so I can distinguish quota from rate limiting.
* As a **security reviewer** I want quota changes to follow the same four-eyes approval and audit trail as other policy changes.

## 3. Scope and non-goals
In scope: daily (UTC) per-consumer/API counter; policy field; gateway enforcement; headers; metrics; UI form field. Out of scope: monthly calendars, billing, per-plan tiers, usage export, refunds, quotas shared across APIs.

## 4. Acceptance criteria
1. **Given** a policy with `dailyQuota=1000`, **when** a consumer makes the 1001st call in the UTC day, **then** the gateway returns `429` with code `quota_exceeded` and `Retry-After` = seconds to the next UTC midnight, and the backend is not called.
2. Successful responses carry `X-Quota-Limit`, `X-Quota-Remaining`, `X-Quota-Reset` (epoch seconds of next midnight UTC).
3. `dailyQuota` must be an integer 1..1,000,000,000 or absent (no quota); invalid values return `422 invalid_policy` from the dry-run and submit endpoints.
4. Changing the quota creates a new policy version via the normal submit/approve flow; existing counters are **not** reset by a policy change within the same UTC day.
5. With two gateways and Redis, the sum of admitted calls never exceeds quota + (replicas x in-flight requests) - tested with concurrency.
6. When Redis is unavailable: policy `quotaFailMode=OPEN` (admit, count locally, emit `QUOTA_DEGRADED`) or `CLOSED` (reject with `503 quota_unavailable`); default `OPEN` with a documented over-admission bound of `quota` per replica.
7. Rate limit (per minute) and quota are independent: either can reject; rate limit is evaluated first (cheaper), quota second.
8. Audit records `POLICY_SUBMITTED/APPROVED` with the quota value in the detail; no consumer identifiers appear in metric labels.

## 5. API contract
`PolicyConfig` gains optional fields (additive, ADR-0010): `dailyQuota` (integer|null), `quotaFailMode` (`OPEN`|`CLOSED`, default `OPEN`). Snapshot `policy` object carries the same fields; gateways ignore unknown fields (N-1 safe). New gateway error code `quota_exceeded` (429) and `quota_unavailable` (503). New response headers above. GraphQL: no change.

## 6. Data model changes
No new tables: quotas live in the policy JSON (`policy_version.config_json`). Counters live in **Redis only**: key `qt:{route:consumer}:{yyyyMMdd}` with TTL 48 h (INCR, atomic). No migration required (verify with `MySqlMigrationIT` that old rows still deserialize with null new fields).

## 7. Security implications
* Quota bypass by key sharing or many keys: quota is per *consumer*, not per key.
* Counter-key injection: keys are built from registry ids/slugs only (never request data).
* Information exposure: headers reveal only the caller's own quota.
* Policy tampering: same four-eyes and audit as all policies; validator caps the value to prevent overflow.
* Abuse of `OPEN` mode during a Redis outage: documented bounded over-admission; alert on `QUOTA_DEGRADED`.

## 8. Failure behaviour
| Dependency | Behaviour |
|---|---|
| Redis down | per `quotaFailMode`; 5 s back-off as in the rate limiter; events on enter/leave |
| Redis slow (> 200 ms) | treated as failure (client timeout) |
| Clock/day boundary | day derived from Redis `TIME` in the Lua script (UTC); counters expire after 48 h |
| Control plane down | gateways keep enforcing the last snapshot's quota |
| Invalid quota in a snapshot | route rejected by the gateway validation (`INVALID_CONFIG`), previous config stays |

## 9. Observability requirements
Counters `gateway_quota_exceeded_total{route}` (bounded label), `gateway_quota_redis_failures_total`; event types `QUOTA_DEGRADED/RECOVERED`; dashboard panel "quota rejections"; alert when exceeded ratio > 20% for a route for 15 min (informational); trace attribute `quota.remaining` (no consumer id label on metrics).

## 10. Performance expectations
One extra atomic Redis call per request when a quota is configured (none otherwise). Budget: +2 ms p95 in-VPC; must be measured with k6 against real Redis before approval; fallback if exceeded: batch increments locally (approximate mode) with a documented error bound. **No numbers are promised until measured.**

## 11. Test scenarios
Unit: validator bounds; Lua script behaviour (Testcontainers Redis); header calculation across midnight (fake clock). Integration: 1001st call -> 429 and backend untouched; policy version flow with quota; N-1 gateway ignores fields. Concurrency: 64 threads x 2 gateways against Redis; exactly quota admitted (+ in-flight bound). Failure: Redis killed mid-test for both fail modes. Security: other consumer's counter unaffected; header spoofing ignored. Performance: k6 scenario `quota` with p95 delta report. E2E: UI form validation + approval.

## 12. Rollout and rollback
Rollout: (1) release control plane accepting the new fields (validators + UI) - no behaviour change; (2) release gateways that enforce them; (3) enable quota on one pilot API with `OPEN`; (4) monitor `QUOTA_DEGRADED`, 429 ratio and p95 for a week; (5) widen. Rollback: set `dailyQuota` to null via a reverted policy (propagates in seconds), or roll gateways back (older gateways ignore the fields, so quotas simply stop being enforced - acceptable only if business agrees; otherwise keep enforcement and fix forward).

## 13. Open questions
Quota reset timezone per partner? Shared quotas across API versions? Grace/overage allowance? Who is notified at 80% usage? (owners: product, API governance)
