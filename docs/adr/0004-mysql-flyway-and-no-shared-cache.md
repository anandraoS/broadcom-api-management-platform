# ADR-0004: MySQL + Flyway as system of record; no shared cache in front of it

* Status: accepted - Date: 2026-10-17

## Context
Registry data is relational (APIs -> versions -> targets; consumers <-> subscriptions), needs constraints, transactions, optimistic locking and an audit trail written atomically with changes.

## Decision
MySQL 8 with Flyway migrations (`ddl-auto=validate`), HikariCP, JPA/Hibernate. Reads for the gateway are served from an in-process snapshot cache (invalidated after commit); no Redis cache sits in front of MySQL.

## Alternatives
1. NoSQL document store: flexible policy documents, weak cross-entity integrity/audit atomicity.
2. PostgreSQL: equally suitable (and richer JSON); MySQL chosen per the mandated stack.
3. Redis cache for registry reads: extra invalidation logic and stale reads for operators with no measured need (registry QPS is tiny).
4. H2 only for tests: dialect drift risk -> `MySqlMigrationIT` runs the same migrations on real MySQL (Docker required) and `local` profile uses H2 only for demos.

## Consequences
+ Strong integrity (unique base paths, FKs), reviewed schema history, simple operations, atomic audit.
- Single writer node (scale-up, read replicas for reporting); schema changes need expand/contract discipline; H2-based tests cannot prove MySQL-specific behaviour (mitigated by the Testcontainers test and checklist item to EXPLAIN on MySQL).

## Review triggers
Registry QPS or snapshot build time becomes a bottleneck (> 100 ms); multi-region active-active writes needed; audit volume demands a dedicated store.
