# 07 - A broken policy deployment

**Symptoms:** minutes after an approved change, consumers of `orders/v1` get 429 on every second call and a partner reports 403s; the change was "just a rate-limit tweak".

**Hypotheses:** (1) limit set too low (valid but wrong); (2) algorithm switched (token bucket -> fixed window) changing burst behaviour; (3) IP allowlist added with the wrong CIDR; (4) `requiredRole` added that tokens lack; (5) gateways on different versions during propagation; (6) a gateway rejected the snapshot (`INVALID_CONFIG`) and still serves the old one while others apply the new one.

**Evidence:** Incidents page -> "Recent configuration changes" (who/what/when, `POLICY_APPROVED version=N`); policy history diff (v N vs N-1); per-gateway `gateway_snapshot_age_seconds` and snapshot `version` in readiness details; events `INVALID_CONFIG`; 429/403 ratios per route.

**Commands**
```bash
curl -s -H "Authorization: Bearer $TOKEN" "$CP/api/v1/apis/$ID/policies" | head      # history with statuses/approvers
curl -s localhost:8081/actuator/health/readiness                                      # applied snapshot version/time
curl -s -H "Authorization: Bearer $TOKEN" "$CP/api/v1/audit?entityType=API&entityId=$ID"
```

**Root cause (exercise):** the submitter changed `rateLimitPerMinute` from 6000 to 600 believing it was per second; the reviewer approved a diff that showed only the number. The policy was valid; the intent was wrong.
**Corrective:** **Revert** to the previous version (approver action, creates v N+1 from v N-1, audited) and watch the dashboard - it applies on the next poll (seconds). Communicate the propagation window to the partner.
**Regression tests:** `fourEyesApprovalSnapshotPropagationAndRevert` (revert path), `invalidSnapshotDoesNotReplaceTheLastKnownGoodConfiguration` (gateway safety net).
**Prevention:** UI shows the *effective per-second rate* and the old->new diff on the approval card; policy-change template requires "expected traffic" and rollback plan; canary policy rollout per gateway group (future work); alert on 429 ratio jump after any policy approval.
