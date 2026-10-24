# Database migration review module

Apply `docs/governance/db-migration-checklist.md`. Key questions for every `V<n>__*.sql`:

1. Is it **backward compatible** with the currently deployed application (N-1) - can both versions run against it during a rolling deploy? (expand/contract)
2. Locks and duration on a large table? Online algorithm available? Batch the backfill?
3. Indexes for new filters/sorts, and do we **drop** superseded ones (as V2 does) - with `EXPLAIN` evidence on MySQL?
4. Constraints and nullability express the invariants; defaults safe; types match the entity mapping (`ddl-auto=validate` must pass).
5. Does it run on MySQL *and* H2 (MySQL mode) - avoid vendor-specific syntax or document it; `MySqlMigrationIT` needs Docker.
6. Rollback/forward-fix plan; destructive steps preceded by backup.
7. Is any audit/telemetry retention affected? Is a purge job needed?

**Never:** edit a merged migration; rename/drop a column in the same release that stops using it; add a NOT NULL column without default to a populated table; put data fixes in application code instead of a reviewed migration.

**Evidence to attach:** migration file, entity diff, `QueryPlanIT` output or MySQL `EXPLAIN FORMAT=TREE`, test results.
