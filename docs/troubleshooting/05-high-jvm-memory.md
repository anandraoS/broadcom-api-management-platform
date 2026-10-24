# 05 - High JVM memory consumption

**Symptoms:** gateway pods approach their 512 MiB limit, `JvmHeapNearLimit` alert, occasional `OOMKilled` restarts; heap after GC keeps trending upward over days.

**Hypotheses:** (1) real leak (unbounded map/queue); (2) legitimately large working set (route table/keys grew); (3) buffered large bodies (request cap/response cap too high); (4) per-key state in the in-memory limiter growing (eviction only above 200k keys); (5) telemetry backlog buffers; (6) off-heap/native growth (direct buffers, metaspace, threads) rather than heap; (7) container limit too small for `MaxRAMPercentage=75`.

**Evidence:** `jvm_memory_used_bytes{area="heap"}` *after* GC (the sawtooth floor), `jvm_memory_used_bytes{area="nonheap"}`, `jvm_buffer_memory_used_bytes`, `process_resident_memory`; heap histogram; heap dump compared at two times; `gateway_ratelimit` key count (`InMemoryRateLimiter.trackedKeys`) if exposed; Kubernetes `OOMKilled` reason.

**Commands**
```bash
jcmd <pid> GC.heap_info
jcmd <pid> GC.class_histogram | head -25
jcmd <pid> GC.heap_dump /tmp/heap.hprof      # then analyse with Eclipse MAT: dominator tree, "leak suspects"
jcmd <pid> VM.native_memory summary          # requires -XX:NativeMemoryTracking=summary
kubectl -n api-platform describe pod <p> | grep -A3 'Last State'
```

**Root cause (exercise):** the anonymous-traffic limiter key includes the client IP; a scan from many source IPs (or `TRUST_FORWARDED_FOR=true` with spoofable XFF) created hundreds of thousands of keys; the 200k eviction threshold plus 2-minute idleness kept them resident.
**Corrective:** lower the key cap / add TTL-based eviction on a schedule, only trust XFF from the ingress (overwrite header there), bound per-IP state; raise memory only after fixing growth.
**Regression tests:** add `InMemoryRateLimiterTest.evictsIdleKeysUnderKeyPressure` (inject clock, insert > cap keys, advance time, assert size drops); soak test with random IPs asserting bounded heap.
**Prevention:** every cache/map has a bound and an eviction policy (architecture invariant), heap-after-GC dashboard panel + alert, `-XX:+HeapDumpOnOutOfMemoryError` to a writable volume, memory limits sized for non-heap overhead (~25%).
