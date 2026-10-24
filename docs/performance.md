# Performance results (measured) and how to reproduce

**Read this first.** These numbers were produced on one Windows 11 laptop (8 logical CPUs) where the load generator, the
gateway, the control plane (in-memory H2), **two** sample backends and the OS all shared the same CPUs, with a closed-loop JDK
client (`performance/LoadProbe.java`, 32 virtual-thread workers, 3 s warm-up, 12-15 s measurement). They are useful for *relative*
comparisons and for finding regressions on the same machine; they are **not** capacity figures and must not be quoted as
production throughput. k6 scripts are provided (`performance/k6/`) but **were not executed** (k6 is not installed in this environment).
Raw outputs: `docs/validation/perf-*.txt`.

| Scenario (32 workers unless noted) | Throughput | p50 | p95 | p99 | max | Errors |
|---|---|---|---|---|---|---|
| **Baseline:** backend directly (`GET /orders/1`) | 1,774 req/s | 10.3 ms | 50.2 ms | 140.3 ms | 951.7 ms | 0 transport, 100% 200 |
| **Gateway** -> backend (API-key auth, token-bucket limiter, 2 targets, snapshot route) run 1 | 919 req/s | 25.2 ms | 88.5 ms | 149.6 ms | 869.7 ms | 0 transport, 100% 200 |
| Gateway run 2 (during resource sampling) | 1,204 req/s | 16.3 ms | 84.1 ms | 177.8 ms | 743.2 ms | 0 / 100% 200 |
| Gateway run 3 (during resource sampling) | 1,353 req/s | 12.9 ms | 85.9 ms | 194.8 ms | 582.1 ms | 0 / 100% 200 |
| Gateway **rejection path** (no API key -> 401, backend not called) | 3,446 req/s | 4.9 ms | 36.0 ms | 89.0 ms | 219.6 ms | 100% 401 (expected) |
| Gateway unknown route (404) | 4,444 req/s | 3.4 ms | 22.4 ms | 90.7 ms | 324.0 ms | 100% 404 (expected) |
| Control plane `GET /api/v1/apis?size=20` (JWT validate + JPA + page), 16 workers | 577 req/s | 19.2 ms | 75.9 ms | 127.5 ms | 270.0 ms | 100% 200 |
| Control plane liveness endpoint (framework floor), 16 workers | 1,221 req/s | 7.1 ms | 44.9 ms | 101.0 ms | 431.4 ms | 100% 200 |

**Run-to-run spread is large** (gateway 919 -> 1,353 req/s) because of JIT warm-up state, GC timing and the competing processes: treat
differences under ~30% as noise.

## What the numbers do and do not say

* Across the three runs the gateway added roughly **3-15 ms at p50** over the direct path and reduced throughput by about **25-50%** *on a shared CPU* (919 / 1,204 / 1,353 vs 1,774 req/s): the extra work is a second HTTP hop (JDK `HttpClient`), buffering, auth hash, limiter, metrics, and the gateway competes with its own backends and load generator for cores. The rejection paths (401/404) are 2-4x cheaper than proxying, confirming that most of the cost is the upstream call, not authentication or route lookup.
* Resource sample (single sample taken during load; later samples were taken after the run ended and are discarded): `process_cpu_usage` gateway 0.197, control plane 0.107, orders-1 0.114, orders-2 0.082 (fractions of **all 8 cores**, i.e. the gateway used about 1.6 cores); gateway heap ~36 MiB. One sample is anecdotal - use Grafana/Prometheus for real utilisation curves.
* Route lookup microbench (`RouteTableTest`, 20,000 routes, includes string building and assertion overhead): **725 ns/lookup** in the recorded run - confirming lookup cost is independent of route count; it is negligible next to network I/O.
* Authentication overhead: not isolated with a dedicated test; the 401 path (hashing + failure response) at ~3.4k req/s vs the 404 path at ~4.4k req/s bounds it from above on this machine.
* Rate limiter overhead: all gateway runs include the limiter; the in-memory limiter is O(1) with a per-key monitor. The Redis limiter adds a network round trip and was **not measured** (no Redis/Docker here).
* Database access: the control plane list endpoint at 577 req/s includes H2 in-memory access; **MySQL numbers were not measured**.
* Concurrent policy updates: correctness is covered by `concurrentPolicySubmissionsYieldUniqueVersionsAndNeverServerErrors`; throughput under contention was not measured (k6 `policy-contention.js` is provided).
* Observability ingestion: `telemetry-ingest.js` provided, not executed.

## Reproduce

```bash
mvn -Plocal-h2 -DskipTests -DskipITs package && scripts/run-local.sh start
performance/prepare-perf-policy.sh                      # raise the orders policy limit via the normal approval workflow
performance/run-probes.sh 32 15                         # control-plane probes
performance/measure-with-resources.sh 32 15             # gateway probe + CPU/heap sampling
k6 run performance/k6/gateway-throughput.js             # if k6 is installed
scripts/run-local.sh stop
```

## Targets to define before a real test (not asserted here)

Set SLOs first (example: 99.9% of requests < 250 ms at 500 req/s per gateway pod, error ratio < 0.5%), then load-test on production-like
hardware with separate machines for generator, gateway, backends and database; capture p50/p95/p99, error ratio, CPU, heap/GC, thread
and connection-pool metrics; compare against the baseline of the direct backend; repeat three times and report the spread.
