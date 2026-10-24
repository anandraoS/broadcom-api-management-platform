# Database migration checklist (Flyway)

- [ ] Versioned file `V<n>__<description>.sql`, never edited after merge; repeatable changes only for views/procs
- [ ] **Backward compatible** with the previous app version (expand -> migrate data -> contract in a later release)
- [ ] No long-running locks: adding columns nullable/with default is online in MySQL 8 (`ALGORITHM=INSTANT/INPLACE`); big indexes/backfills run in batches or with `gh-ost`/`pt-online-schema-change`
- [ ] Every new filter/sort has an index; `EXPLAIN`/`EXPLAIN ANALYZE` on MySQL with production-like volume attached to the PR (H2 plans only prove index selection)
- [ ] Constraints (PK/FK/UNIQUE/CHECK) express invariants; nullability intentional; charset `utf8mb4`; timestamps UTC `TIMESTAMP(3)`
- [ ] No data loss: destructive steps (DROP/ALTER type) preceded by backup and a verified restore path
- [ ] Rollback plan: down-migration or forward-fix script; app rollback tolerated by the schema
- [ ] Runs on both MySQL (`MySqlMigrationIT`) and the H2 test profile - or the file documents why not
- [ ] Runtime DB user has DML only; migrations run with a separate DDL account (production)
- [ ] Retention/size impact estimated (audit and telemetry tables); partitioning noted if growth is high
- [ ] Idempotency/locking considered for application start (multiple replicas run Flyway concurrently - Flyway locks; prefer a pre-deploy Job)
- [ ] Release note mentions the migration and expected duration
