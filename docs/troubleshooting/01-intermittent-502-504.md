# 01 - Gateway returns intermittent 502/504

**Symptoms:** ~5-10% of calls to `orders/v1` fail with 502 or 504; retries by clients sometimes succeed; no deploy in the last hour. Alert `GatewayHighErrorRate`.
**Reproduce:** register `/flaky?failPercent=30` and `/slow?ms=4000` through the gateway with `timeoutMs=3000`.

**Initial hypotheses:** (1) one of two targets is unhealthy; (2) policy timeout < backend latency tail; (3) backend connection resets (keep-alive idle timeout mismatch); (4) retries amplifying load; (5) network policy / DNS flaps; (6) breaker flapping.

**Evidence to collect**
* Split by code: `sum by (status_class)(rate(gateway_requests_seconds_count{route="orders/v1"}[5m]))`, and 502 vs 504 in logs (`code":"bad_gateway"` vs `gateway_timeout`).
* Per-target view: events `TARGET_UNHEALTHY`, `CIRCUIT_OPEN` (Events page), `gateway_backend_timeouts_total`.
* Backend latency histogram vs policy `timeoutMs`; backend pod restarts.
* A failing request's correlation id -> logs (`cid=`) -> trace in Jaeger (which span is slow/failed).

**Commands**
```bash
kubectl -n api-platform logs deploy/gateway --since=15m | grep -E 'backend call failed|circuit|unhealthy'
kubectl -n api-platform get pods -l app=orders-service -o wide ; kubectl -n api-platform describe pod <p> | tail -20
curl -s localhost:8081/actuator/prometheus | grep -E 'gateway_(backend_timeouts|retries)_total'
```

**Root cause (exercise):** `orders-service-2` is intermittently slow (GC pauses) -> requests to it exceed the 3 s budget (504) and connection resets after its Tomcat keep-alive timeout produce 502; weighted round-robin keeps sending it ~1/3 of traffic until the breaker's 5 *consecutive* failures, which never happen because successes interleave.
**Corrective action:** take the target out of rotation (reviewed target removal) -> fix backend heap/keep-alive; set policy `timeoutMs` from measured backend p99 + margin.
**Regression tests:** `backendTimeoutReturns504`, `unreachableBackendReturns502NotAnInternalError`, `secondTargetTakesOverWhenFirstIsUnhealthy`; add a gateway test for partial-failure ejection if outlier detection is implemented.
**Prevention:** health checks that exercise real dependencies, rate-based/outlier ejection (ADR-0006 trigger), SLO alert on 5xx ratio, align keep-alive timeouts (backend idle timeout > gateway pool idle), canary rollouts.
