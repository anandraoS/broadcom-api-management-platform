# 04 - MySQL connection-pool exhaustion

**Symptoms:** console requests time out after 3 s with 500s (`Connection is not available, request timed out after 3000ms`); `hikaricp_connections_pending > 0`; gateways keep serving traffic (they do not use the DB).
**Reproduce:** run the control plane with `DB_POOL_MAX=2` and fire concurrent `GET /api/v1/apis` while a long transaction holds connections.

**Hypotheses:** (1) slow query holding connections; (2) transaction held open across a remote call; (3) connection leak (not closed/returned); (4) pool too small for the load; (5) MySQL max_connections / failover; (6) lock wait (row lock on `api_definition` from concurrent edits).

**Evidence:** `hikaricp_connections_active/pending/max`, `..._usage_seconds_max` (how long connections are held), `..._acquire_seconds_max`; Hikari leak-detection warnings (`leak-detection-threshold=20s`); `SHOW FULL PROCESSLIST`, `SELECT * FROM performance_schema.data_lock_waits`, slow query log; thread dump to see what threads hold connections.

**Commands**
```sql
SHOW FULL PROCESSLIST;
SELECT * FROM information_schema.innodb_trx ORDER BY trx_started LIMIT 10;
EXPLAIN ANALYZE SELECT ... ;   -- for the top query in the slow log
```
```bash
jcmd <pid> Thread.print | grep -B2 -A12 'HikariPool\|JdbcResourceLocalTransactionCoordinator'
```

**Root cause (exercise):** a new report endpoint loaded all audit rows for an entity without a `LIMIT` and used the old `idx_audit_entity` (filter only) - each call held a connection for seconds; 15 concurrent calls exhausted the pool. (This mirrors the real query-plan finding fixed by migration V2 and the paginated audit API.)
**Corrective:** paginate/limit, add `idx_audit_entity_ts`, set statement timeout (`socketTimeout`/`max_execution_time`), keep transactions short and read-only, then right-size the pool.
**Regression tests:** `QueryPlanIT` (intended indexes), registry/audit pagination tests; add a test that boots with `DB_POOL_MAX=1` and verifies fast failure (3 s) rather than hanging.
**Prevention:** pool saturation alert (`HikariPoolSaturated`), page-size caps (<= 100), review `EXPLAIN` in PRs (`docs/governance/db-migration-checklist.md`), no remote calls inside transactions, load test list endpoints with realistic row counts.
