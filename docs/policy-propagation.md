# Policy configuration and propagation

## Mechanism: versioned, pull-based, eventually consistent

```
 approve (tx commit) ─▶ ConfigChanged event ─AFTER_COMMIT─▶ snapshot cache invalidated
 gateway poll (every snapshotPollMs, default 5 s) ─ GET /internal/v1/gateway/snapshot + If-None-Match ─▶ 304 or new snapshot
 gateway: validate every route/target ─▶ build immutable RouteTable ─▶ atomic swap ─▶ persist last-known-good to disk
```

* **Version identity:** `version` = content hash; used as ETag, so an unchanged config costs one `304`.
* **Validation on apply (gateway):** route path format, complete policy, >= 1 target, every target host in the gateway's *own* allowlist. Invalid routes are dropped with an `INVALID_CONFIG` event; a snapshot with **no** valid routes (but some offered) is rejected entirely and the previous config stays (`invalidSnapshotDoesNotReplaceTheLastKnownGoodConfiguration`, `partiallyInvalidSnapshotAppliesOnlyTheValidRoutes`).
* **Atomicity:** `SnapshotHolder` swaps one immutable object via an `AtomicReference`; a request sees either the old or the new config, never a mix; no locks on the request path.
* **Fail closed:** the control plane only publishes ACTIVE APIs that have an ACTIVE policy and a valid target.
* **Consistency rule (control plane cache):** the in-memory snapshot is invalidated *after commit*, so gateways can never receive uncommitted state; a 30 s scheduled invalidation covers missed events in multi-replica control planes.

## What the system does and does NOT guarantee

| Claim | True? | Evidence |
|---|---|---|
| A committed approval reaches every healthy gateway | Yes, **eventually** | bounded by poll interval (5 s) + control-plane cache invalidation (immediate on the replica that handled the commit; <= 30 s on other replicas) |
| Propagation is instantaneous / globally simultaneous | **No** | gateways apply at different moments; during the window, two gateways may enforce different versions |
| Measured delay | Local run: **6.9-8.4 s** from `approve` to the first enforced `429` (2 s poll; includes the test script's own 0.5 s loop and a cached-snapshot window) | `scripts/smoke-test.sh` output in `docs/validation/` |
| A bad policy can be applied | Only if it passes validation + four-eyes review; business-logic mistakes (e.g. limit too low) still need review | `PolicyValidator`, approvals |
| A rollback is faster than the change | Yes - revert is one call by an approver and propagates the same way | `fourEyesApprovalSnapshotPropagationAndRevert` |

Operational consequence: **policy changes are not an emergency brake** at second-level precision. For an immediate block use the
consumer *disable* or key *revoke* (same propagation path) plus the ingress/WAF rule, and plan for the propagation window.

## Control plane unavailable

| Situation | Behaviour |
|---|---|
| Control plane down, gateways running | Gateways keep serving the **last-known-good** snapshot indefinitely; a `SNAPSHOT_STALE` event (after 3 failed polls) and `gateway_snapshot_age_seconds` alert; **no policy/key/consumer changes take effect** (a revoked key keeps working until recovery) |
| Gateway restarts while control plane is down | If `SNAPSHOT_CACHE_FILE` is on a writable volume, the pod loads the cached snapshot at start and becomes Ready; without it, the pod stays NotReady (it has no config) |
| Control plane returns garbage / 5xx | Poll fails, previous snapshot kept; 4xx from telemetry ingestion drops the batch (counted), 5xx retries the same batch id |
| Telemetry path down | Per-minute aggregates buffer up to 2000 buckets / 2000 events, oldest dropped (`gateway_telemetry_dropped_total`); requests unaffected |

## Deploying a policy change safely (runbook)

1. Edit in the UI/API -> **Validate (dry run)** -> **Submit** with a comment explaining intent and rollback.
2. A different `POLICY_APPROVER` reviews the diff of rate/timeout/auth/IP fields and approves (re-validated server-side).
3. Watch the dashboard for the changed route: 429/401/5xx ratios and p95 for 10 minutes (propagation completes within ~1 poll interval).
4. If bad: **Revert** to the previous version (creates a new version, audited). Post-incident: update the policy review checklist.
