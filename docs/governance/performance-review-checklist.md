# Performance review checklist

**Before coding:** what is the hot path? What is the budget (latency, throughput, memory) and where does it come from (SLO)? Is there a baseline?

**Design/code review**
- [ ] No blocking calls, regex compilation, JSON mapper creation, DNS lookups or reflection on the per-request path (or they are cached/bounded)
- [ ] Allocation: body buffering bounded and pre-sized; no per-request large temporary collections; no unbounded caches/maps (eviction + max size)
- [ ] Concurrency: no `synchronized` around I/O (virtual-thread pinning), lock scope small, per-key state not global
- [ ] I/O: timeouts, pooling/keep-alive, no N+1 queries, pagination, indexes validated with `EXPLAIN`
- [ ] Retries/backoff cannot amplify load; bulkheads/limits sized
- [ ] Telemetry: label cardinality bounded, sampling rate agreed, no per-request log at INFO with payloads

**Measurement**
- [ ] Before/after numbers on the same hardware (>= 3 runs, report spread; not on a laptop sharing CPU with the SUT for capacity claims)
- [ ] p50/p95/p99, error ratio, throughput, CPU, heap/GC pause, thread/connection-pool metrics captured
- [ ] Failure-mode run (backend slow/down, Redis down) shows graceful degradation
- [ ] Profiling evidence when a regression is found (JFR / async-profiler flame graph, heap histogram)

**Release gate:** p95 within +10% of baseline (or an approved exception), no new GC pressure, memory stable in a soak run, HPA/limits validated. Record results in the PR and `docs/performance.md`.
