# 02 - Latency increases after a new release

**Symptoms:** p95 for `orders/v1` rose from ~90 ms to ~600 ms 10 minutes after release 1.0.1; error rate unchanged; CPU on the gateway normal.

**Hypotheses:** (1) backend regression (new query/N+1); (2) gateway change (e.g. extra allocation, DNS resolve per request, logging level); (3) policy change shipped with the release (timeout/retry/limiter algorithm); (4) downstream dependency; (5) cold JVM/JIT after rollout; (6) noisy neighbour/limits (CPU throttling).

**Evidence:** Grafana p50/p95/p99 before/after (use the release marker/annotation), `histogram_quantile` per route; compare trace spans (gateway vs backend) for slow requests; audit trail around the deploy time (Incidents page "Recent configuration changes"); container CPU throttling (`container_cpu_cfs_throttled_seconds_total`); GC pause rate; `gateway_retries_total` (hidden latency from retries).

**Commands**
```bash
kubectl -n api-platform rollout history deploy/gateway ; kubectl -n api-platform get hpa
jcmd <pid> Thread.print | grep -c 'BLOCKED'            # contention
jcmd <pid> GC.heap_info ; jfr start name=lat duration=60s filename=/tmp/lat.jfr   # then inspect hot methods
```

**Root cause (exercise):** the release added a per-request `InetAddress.getAllByName()` DNS resolution (the SSRF re-check, `DestinationGuard.addressesSafe`) without caching; in a cluster with slow DNS this adds ~hundreds of ms under load. (This is a *real design cost* of the control: the JVM caches positive lookups for 30 s by default, but negative/slow resolvers or `networkaddress.cache.ttl=0` remove that.)
**Corrective:** set `networkaddress.cache.ttl=30` and add a bounded TTL cache of safe-resolution results per host; roll back via `helm rollback` while fixing.
**Regression tests:** add a micro-benchmark/unit test asserting repeated `allowed(url)` calls perform one resolution per TTL (inject a resolver); load test comparing p95 before/after in CI (`performance/k6/gateway-throughput.js` thresholds).
**Prevention:** performance gate in the release checklist (p95 within 10% of baseline), canary with automatic rollback on SLO burn, dashboards with deploy annotations, keep security re-checks cheap (cache with short TTL).
