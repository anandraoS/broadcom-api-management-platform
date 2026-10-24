# Performance review module

Never claim a performance improvement or regression without numbers from a command you ran. Use `docs/governance/performance-review-checklist.md` as the checklist and `docs/performance.md` as the baseline.

**Procedure**
1. Identify the hot path (gateway: `GatewayFilter` -> `Authenticator` -> `RateLimiter` -> `ProxyService`; control plane: list/search endpoints, snapshot build).
2. Capture a baseline: `performance/run-probes.sh`, `performance/measure-with-resources.sh` (same machine, >= 3 runs, report spread).
3. Make **one** change; re-measure; compare p50/p95/p99, throughput, CPU, heap/GC.
4. If a regression: profile (JFR / async-profiler), look at allocations and blocking, inspect pool/semaphore metrics.
5. Record results (command, environment, numbers, caveats) in the PR; update `docs/performance.md` only with measured values.

**Review questions:** per-request DNS/regex/ObjectMapper creation? unbounded buffers or maps? locks around I/O? N+1 queries? missing index (EXPLAIN)? retry amplification? metrics label cardinality? log volume at INFO?

**Anti-patterns for assistants:** "optimizing" without a benchmark; micro-optimising cold paths; adding caches without invalidation rules; quoting benchmark numbers from memory; tuning JVM flags before reducing allocation; comparing runs on different hardware.
