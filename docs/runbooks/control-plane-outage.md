# Runbook: control plane unavailable

**Alert:** `GatewayConfigStale` (snapshot age > 120 s), `TargetDown{job="control-plane"}`, `SNAPSHOT_STALE` events.

**What still works:** all traffic already routable (gateways serve the last-known-good snapshot; the on-disk copy survives container restarts). **What does not:** policy/route/consumer/key changes (including *revocations*), the console, new telemetry history (buffered, then dropped oldest).

1. Confirm scope: `kubectl -n api-platform get pods -l app=control-plane`; `GET /actuator/health` on a control-plane pod; gateways `/actuator/health/readiness` (version, appliedAt).
2. Check dependencies: MySQL reachable? connection pool saturated (`hikaricp_connections_pending`)? Recent migration or deploy? NetworkPolicy changes blocking gateway -> control plane?
3. **Do not restart gateways** (a restarted gateway without cache cannot become Ready); do not deploy new gateway versions during the outage.
4. If a credential must be revoked *now* and the control plane cannot propagate: block at the edge (WAF/ingress rule) and, as a last resort, restart gateways after the control plane is back.
5. Recover: roll back the last control-plane release or fix the database; confirm `gateway_snapshot_age_seconds` returns to < 10 s and `SNAPSHOT_STALE` stops; verify audit continuity.
6. Follow-up: how long was the window? Were revocations pending? Was the on-disk snapshot cache present? Update capacity/alerts.
