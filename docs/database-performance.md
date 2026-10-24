# Database and performance engineering

## Schema design (MySQL 8, Flyway)

* Migrations: `V1__core_schema.sql` (all tables, constraints, indexes), `V2__audit_entity_time_index.sql` (a change driven by the query-plan review below). `ddl-auto=validate`: Hibernate never changes the schema.
* Integrity: FKs between aggregates; `UNIQUE` on `slug`, `base_path` (two API versions cannot claim one gateway path), `(api_id, version_no)`, `client_id`, `key_prefix`, `(batch_id, route_slug, bucket_start)`; `CHECK (weight BETWEEN 1 AND 100)`.
* Types: `TIMESTAMP(3)` UTC, `LONGTEXT` for policy JSON/OpenAPI, `BIGINT` ids, `BOOLEAN`; `utf8mb4`.
* Optimistic locking: `api_definition.version` (`@Version`) + explicit version check on `PUT` -> `409 concurrent_modification`. Tests: `optimisticLockingRejectsStaleUpdates`.
* Transactions: services are `@Transactional`; audit is `Propagation.MANDATORY` (cannot be written outside the changing transaction); reads are `readOnly=true`; `open-in-view=false` so lazy loading cannot leak into controllers; rejection audits use `noRollbackFor=ApiException` so the 422 does not roll back its own audit row.
* Connection pool (HikariCP): `maximum-pool-size 15`, `connection-timeout 3 s` (fail fast under saturation), `max-lifetime 25 min` (< MySQL `wait_timeout`), `leak-detection-threshold 20 s`. Size to what MySQL can execute concurrently, not to the number of threads (virtual threads make thread count irrelevant; the pool is now the concurrency limit).

## Indexes and query plans

| Query | Index | Plan evidence |
|---|---|---|
| API search (owner, lifecycle) | `idx_api_owner_lifecycle (owner_team, lifecycle)` | `docs/validation/query-plans-h2.txt` |
| Traffic window per route | `idx_bucket_route_time (route_slug, bucket_start)` | same file (3,000 synthetic rows) |
| Audit history by entity, newest first | `idx_audit_entity_ts (entity_type, entity_id, ts)` | same file |

**Review finding -> change:** the first plan for the audit query used `idx_audit_entity (entity_type, entity_id)` and still had to
sort every matching row by `ts`. `V2` replaces it with a composite index ending in `ts` so the newest rows are read first and the
scan stops at `LIMIT 25`. The test `QueryPlanIT` asserts the intended index is chosen. **Caveat:** these plans come from H2 in MySQL
mode on ~3k rows; they prove index *selection*, not MySQL cost or absence of `Using filesort`. Confirm on MySQL 8 with
`EXPLAIN FORMAT=TREE` / `EXPLAIN ANALYZE` against production-like volume before release (checklist item).

Query hygiene in the code: all repository queries are parameterised JPQL; the registry search fetches a page, not a list; the snapshot
query uses one `join fetch` for versions and loads targets lazily inside one read-only transaction (bounded by the number of active
versions, cached in memory between changes); user-supplied sort fields are whitelisted.

## Caching and consistency rules

| Cache | Content | Invalidation | Consistency |
|---|---|---|---|
| Control plane snapshot (`AtomicReference`) | Full gateway configuration | After-commit event + 30 s timer | Never newer than committed data; can be stale up to 30 s on other replicas |
| Gateway snapshot (`SnapshotHolder`) | Immutable route table + keys | Poll + ETag | Eventually consistent (<= poll interval + control-plane staleness) |
| Redis | Rate-limit counters only | TTLs | Ephemeral by design; loss = brief over-admission |

No Redis cache sits in front of MySQL: the registry is low-QPS and read-your-writes semantics matter for operators; adding a shared cache
would add invalidation complexity without a measured need (ADR-0004).

## Audit retention

`audit_event` is append-only and **not** purged by the application. Recommended: monthly range partitions on `ts`, export older partitions
to object storage (WORM/legal hold as required), then `DROP PARTITION`; keep >= the compliance period (often 1-7 years). Telemetry
tables are purged after 7 days (`RetentionJob`, `app.retention.telemetry-days`); idempotency records after 24 h.

## Performance testing

| Concern | Tool | Where |
|---|---|---|
| Gateway throughput/latency, auth overhead, rate-limit path | **k6** scripts (`performance/k6/*.js`) - **not executed** here (k6 is not installed); **LoadProbe** (JDK, no dependencies) - executed, results in `docs/performance.md` | `performance/` |
| Route lookup | `RouteTableTest.lookupStaysFastWithTwentyThousandRoutes` (prints ns/lookup) | gateway tests |
| Rate limiter cost and contention | `InMemoryRateLimiterTest` (correctness under 64 threads); k6 scenario `rate-limit` | |
| DB access | `performance/k6/control-plane-read.js` against the registry list; `QueryPlanIT` for plans | |
| Observability ingestion | `performance/k6/telemetry-ingest.js` (batches of 500 buckets) | |
| Concurrent policy updates | `performance/k6/policy-contention.js` (many submits against one API; expects only 201/403/409/422, no 500) | |

## Investigation guide (what to look at first)

| Symptom | Evidence | Typical cause / fix |
|---|---|---|
| CPU saturation | `process_cpu_usage` ~1.0 per core, run queue; `jcmd <pid> Thread.print`/async-profiler flame graph | Hot loop, regex backtracking, JSON (de)serialisation, TLS; fix the hot path, scale out gateways (HPA on CPU) |
| Memory pressure | `jvm_memory_used_bytes{area="heap"}` after GC trending up; `jcmd GC.heap_info`; heap dump `jcmd <pid> GC.heap_dump` and open in MAT | Unbounded cache/queue, per-key maps (limiter has an eviction bound), large buffered bodies (caps: request limit, 10 MiB response) |
| GC | `jvm_gc_pause_seconds_sum` rate, `-Xlog:gc*` | Allocation churn from body buffering; G1 default is fine; raise heap or reduce allocation before tuning flags |
| Pool exhaustion | `hikaricp_connections_pending > 0`, `acquire_seconds_max`, `usage_seconds` | Slow query/long transaction; fix query, shorten transactions, then (last) raise the pool |
| Thread starvation | Virtual threads rarely starve, but **pinned** carrier threads (synchronized + blocking I/O) do: `-Djdk.tracePinnedThreads=full`; semaphores/bulkheads saturated | Replace blocking `synchronized` sections that wrap I/O; cap concurrency |
| Slow queries | MySQL slow log (`long_query_time=0.5` in compose), `performance_schema`, `EXPLAIN ANALYZE` | Missing/unused index, N+1, large offset pagination (prefer keyset) |
| High-cardinality telemetry | Prometheus `/api/v1/status/tsdb`, series count per metric, `topk(10, count by (__name__)({__name__=~".+"}))` | Unbounded labels; remove label, aggregate, or move detail to logs/traces |
