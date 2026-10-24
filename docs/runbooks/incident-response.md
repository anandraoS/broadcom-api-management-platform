# Incident response runbook

## Roles and severity
Incident commander (IC), operations lead, communications lead, scribe. **SEV1** customer-facing outage / data exposure (page, 15-min updates); **SEV2** degraded
(error budget burning fast); **SEV3** minor/ticket.

## First 10 minutes
1. Acknowledge the page; create the incident channel; IC announced; start the timeline (scribe).
2. **Assess impact** from symptoms, not causes: which APIs/consumers, since when, how many requests (dashboard: *Gateway Overview*; console: *Incident investigation*).
3. **Stabilise before diagnosing:** if a change correlates (Incidents page -> recent configuration changes, deploy history) **revert it** (policy revert, `helm rollback`).
4. Communicate: status update template - impact, start time, what we know, next update time.

## Elevated 5xx
Dashboard: status-class panel (502/503/504?) -> Events (`CIRCUIT_OPEN`, `TARGET_UNHEALTHY`) -> backend health/restarts -> correlation id of a failing request -> trace in Jaeger. Mitigate: remove/replace bad target (reviewed change), scale backend, disable retries if a retry storm is suspected, increase timeout only with evidence. Details: `docs/troubleshooting/01-intermittent-502-504.md`.

## Elevated latency
p95 by route -> gateway vs backend span split -> recent releases/policy changes -> JVM/GC/Hikari panels -> `docs/troubleshooting/02-latency-after-release.md`.

## Auth failure or 429 spike
Is it one consumer (expired/rotated key, misconfigured client) or many (attack)? Check key expiry events and the audit trail. Attack: tighten IP allowlist/limits via an expedited reviewed policy change plus edge WAF rule; remember propagation takes seconds.

## Security incident (credential leak)
Revoke the key/consumer immediately (propagates in seconds), rotate the gateway token/JWT secret if suspected, preserve audit + logs, notify security, assess exposure window using audit events and access logs.

## Closing
Confirm recovery via SLO metrics for 30 minutes; communicate resolution; schedule the post-incident review within 3 business days (`docs/leadership/post-incident-review-template.md`); file follow-ups with owners and dates.
