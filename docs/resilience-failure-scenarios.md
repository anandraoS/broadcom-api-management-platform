# Distributed-systems resilience and failure scenarios

## Primitives (all in `gateway/`, each with tests)

| Primitive | Implementation | Test evidence |
|---|---|---|
| Timeouts | Policy `timeoutMs` = **end-to-end budget** including retries; connect timeout 2 s; Tomcat connection timeout 10 s; Lettuce 200 ms | `backendTimeoutReturns504` (returns at ~300 ms, backend took 1.5 s) |
| Retries with backoff + jitter | Safe methods only (GET/HEAD/OPTIONS), policy-enabled, transient failures (connect error, 502/503/504), full jitter `rand(0..min(base*2^n, 2 s))`, <= 3 attempts, never past the budget | `safeMethodsAreRetriedOnTransientFailureButPostIsNot`, `retriesAreBoundedByAttemptLimit` |
| Circuit breaker | Consecutive-failure breaker per target, single half-open probe | `CircuitBreakerTest` (4), `circuitBreakerOpensAndGatewayDegradesWithoutCallingTheFailingBackend` |
| Bulkhead / concurrency limit / backpressure | Per-route semaphore (`max-concurrent-per-route`), immediate `503 + Retry-After` instead of queueing | `bulkheadShedsLoadWhenARouteIsSaturated` |
| Health checks | Active probes every 5 s; unhealthy targets leave rotation; transitions emit events | `secondTargetTakesOverWhenFirstIsUnhealthy` |
| Graceful shutdown | `server.shutdown=graceful`, 25 s drain; Kubernetes `preStop sleep 10` + `terminationGracePeriodSeconds 40` | config (`application.yml`, manifests) - not exercised by an automated test |
| Idempotency | `Idempotency-Key` on API creation; idempotent telemetry ingestion; revert-as-new-version | `idempotencyKeyReplays...`, `telemetryIngestionIsIdempotent...` |
| Eventual consistency | Config propagates by polling; documented window | `docs/policy-propagation.md` |
| Async processing / DLQ | Telemetry shipping is *batched async HTTP* with at-least-once + idempotent receiver. No broker. A DLQ equivalent: batches the control plane rejects (4xx) are counted (`gateway_telemetry_dropped_total`) and dropped; poison data cannot block the queue | ADR-0007 |
| Partial failure | Per-target breaker/health, partially valid snapshots apply the valid routes only | `partiallyInvalidSnapshotAppliesOnlyTheValidRoutes` |
| Correlation across boundaries | `X-Correlation-Id` + `traceparent` to backends; stored in audit/events | `routesTrafficRewritesPathAndPropagatesCorrelationId` |
| Service discovery / route registration | Registry-driven: APIs register targets in the control plane; gateways discover routes through the snapshot (no static route files). DNS names in Kubernetes/compose resolve service instances | `docs/architecture.md` |

Why no Kafka/RabbitMQ: see ADR-0007. A broker is justified when events must survive consumer downtime for hours, fan out to many
consumers, or be replayed; telemetry aggregates here are small, loss-tolerant and bounded, so a broker would add an operational
dependency without a requirement that needs it.

## Scenario playbooks (detection -> impact -> containment -> recovery -> prevention)

### 1. A backend service becomes unavailable
* **Detect:** `TARGET_UNHEALTHY` / `CIRCUIT_OPEN` events; 502/503 ratio up in Grafana; alert `GatewayHighErrorRate`; Incidents page rule `CIRCUIT_BREAKER_OPEN`.
* **Impact:** with >= 2 healthy targets: none after the health check (<= 5 s) / breaker (5 failures); with one target: `503 no_healthy_backend` + `Retry-After` for that API only; other APIs unaffected (per-route isolation).
* **Contain:** breaker fails fast (no thread pile-up); bulkhead limits concurrency; remove the target (reviewed change) or scale the backend.
* **Recover:** half-open probe after 10 s closes the breaker automatically; health check re-admits the target; `TARGET_RECOVERED` event.
* **Prevent:** >= 2 targets across zones, readiness probes on backends, load-test failover, alert on single-target APIs.

### 2. Redis becomes unavailable
* **Detect:** `RATE_LIMITER_DEGRADED` event, `gateway_ratelimit_redis_failures_total`, alert `RateLimiterRedisFailing`.
* **Impact:** none to availability; limits become approximate (LOCAL mode, `limit / replicas`) or requests get `503` (CLOSED mode, by policy choice).
* **Contain:** 5 s back-off prevents timeout storms; Lettuce 200 ms timeout; Redis excluded from readiness.
* **Recover:** automatic re-try after back-off; `RATE_LIMITER_RECOVERED`. Counters restart empty (a brief over-admission window).
* **Prevent:** Redis replica/failover (managed service), alert on latency not only errors, choose CLOSED only where over-admission is costly.

### 3. A slow database query
* **Detect:** Hikari `pending` > 0 (alert), control-plane request p95 up, MySQL slow log (`long_query_time=0.5` in compose), traces show DB spans.
* **Impact:** console/management API slow; **traffic unaffected** (gateway never queries the DB; it uses the snapshot, cached in the control plane).
* **Contain:** `connection-timeout=3 s` fails fast instead of queueing threads; pagination caps; `readOnly` transactions.
* **Recover:** kill the offending query, add the missing index (Flyway migration), roll back the release.
* **Prevent:** `EXPLAIN` in review (`docs/database-performance.md`), indexes on filter/sort columns, query timeouts, limit pool size to what MySQL can serve.

### 4. A gateway instance restarts
* **Detect:** pod restart count, `up == 0` alert, readiness flap.
* **Impact:** in-flight requests on that instance get connection resets unless drained gracefully (SIGTERM -> readiness fails -> 25 s drain). Local limiter state (LOCAL mode) resets; Redis state survives.
* **Contain:** `maxUnavailable: 0` rolling updates, PDB `maxUnavailable: 1`, >= 3 replicas, preStop sleep for endpoint removal.
* **Recover:** the pod loads the on-disk snapshot (if the volume persists across *container* restarts) or fetches a fresh one and becomes Ready only then.
* **Prevent:** keep control-plane reachable during rollouts; never roll gateways and the control plane simultaneously; surge capacity.

### 5. An invalid policy update
* **Detect:** rejected at submit (`422 invalid_policy`, audited as `POLICY_REJECTED_INVALID`); or at apply by the gateway (`INVALID_CONFIG` event).
* **Impact:** none - invalid policies never activate; a snapshot with no valid routes is rejected whole; partially valid snapshots apply only valid routes.
* **Contain/Recover:** last-known-good stays active; fix and resubmit, or revert.
* **Prevent:** dry-run validation in the UI, validation parity (control plane `PolicyValidator` and gateway `problem()` checks), contract test fixtures for the snapshot, four-eyes review.
* **Residual risk:** a *valid but wrong* policy (e.g. limit 1/min) is only caught by humans/monitoring.

### 6. The observability backend becomes unavailable
* **Detect:** Prometheus `up{job=...}==0`, missing Grafana data, `gateway_telemetry_dropped_total` rising, OTLP exporter warnings.
* **Impact:** none to traffic; loss of dashboards/traces for the outage window (metrics scrape resumes; traces dropped; control-plane aggregates buffered up to 2000 buckets).
* **Contain:** exporters are asynchronous and bounded; telemetry shipper never blocks requests.
* **Recover:** automatic; backfill is limited to what was buffered.
* **Prevent:** run the collector as a redundant pair, alert on scrape failures independently of the thing being observed, keep a minimal out-of-band health check.

### 7. A retry storm caused by a failing dependency
* **Detect:** `gateway_retries_total` spikes, backend request rate >> gateway request rate, latency climbs, breaker events.
* **Impact:** without controls, retries multiply load on an already failing backend (3x with 3 attempts) and keep it down.
* **Contain (implemented):** retries only for safe methods and only when the policy enables them; <= 3 attempts; exponential backoff **with jitter** (de-synchronises clients); the end-to-end budget caps total work per request; the breaker stops traffic after 5 consecutive failures; bulkhead caps concurrency.
* **Recover:** disable `retryEnabled` via an expedited reviewed policy change if needed; breaker half-open probes limit recovery load.
* **Prevent:** retry budgets (e.g. retries <= 10% of requests) as a future improvement, client-side backoff documentation for consumers, load tests that include failing dependencies.
