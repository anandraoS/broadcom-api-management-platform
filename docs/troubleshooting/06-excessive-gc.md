# 06 - Excessive garbage collection

**Symptoms:** latency spikes every few seconds, `rate(jvm_gc_pause_seconds_sum[1m])` > 5% of wall time, CPU high but throughput flat, p99 >> p50.

**Hypotheses:** (1) high allocation rate (per-request buffering of bodies, string building, JSON); (2) heap too small for the live set (frequent mixed collections); (3) humongous allocations (G1 regions) from large byte arrays; (4) too-small young generation due to container limits; (5) explicit `System.gc()`; (6) CPU throttling making GC threads slow (cgroup limits).

**Evidence:** GC logs (`-Xlog:gc*:file=/tmp/gc.log`), pause durations and causes (`G1 Humongous Allocation`, `Allocation Failure`), allocation rate (`jdk.ObjectAllocationSample` in JFR), `jvm_gc_pause_seconds_max`, live-set size after full GC, container CPU throttling.

**Commands**
```bash
jcmd <pid> JFR.start name=gc settings=profile duration=120s filename=/tmp/gc.jfr
jcmd <pid> VM.flags | tr ' ' '\n' | grep -E 'Xm|G1|MaxRAM'
jstat -gcutil <pid> 1000 10
```

**Root cause (exercise):** a "large payload" consumer posts 9 MiB bodies; the gateway reads them into a `ByteArrayOutputStream` that doubles its backing array (16 KiB -> ... -> 16 MiB), producing humongous allocations and frequent mixed GCs; the policy allowed `maxRequestBytes=10 MiB`.
**Corrective:** lower `maxRequestBytes` for that API (reviewed policy change), pre-size the buffer from `Content-Length` (bounded by the limit), consider streaming/chunked passthrough for large uploads (ADR-0002 trigger), size heap to 2-3x the live set.
**Regression tests:** unit test that the body reader pre-sizes from `Content-Length`; load test with 5 MiB bodies asserting GC pause budget; policy validator already caps at 10 MiB (`PolicyValidatorTest.requestSizeIsCapped`).
**Prevention:** per-API size limits default small (64 KiB here), GC-pause SLO panel, alert on pause share, allocation profiling in perf review (`docs/governance/performance-review-checklist.md`).
