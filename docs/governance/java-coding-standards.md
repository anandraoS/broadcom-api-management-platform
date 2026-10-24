# Java coding standards

1. **Java 21** language level; records for DTOs/value objects; sealed/enum + `switch` expressions for closed sets; `var` only when the type is obvious.
2. **Layering:** `web` (controllers, DTOs) -> `service` (transactions, authorization, audit) -> `repo` (Spring Data) -> `domain` (entities). Controllers contain no business logic; entities are never returned from controllers; services return DTOs.
3. **Transactions:** on service methods only; reads `readOnly=true`; no remote calls inside a transaction; audit via `AuditService` (`MANDATORY`).
4. **Authorization:** `@PreAuthorize` for roles **and** object-level checks in the service layer (`AccessGuard`) - REST and GraphQL share services.
5. **Validation:** Bean Validation on request records for syntax; domain/semantic rules in dedicated validators (`PolicyValidator`, `DestinationValidator`) that are pure and unit-tested.
6. **Persistence:** parameterised JPQL/Spring Data only (never string-concatenated SQL); whitelist sort fields; explicit fetch strategy (no N+1); `@Version` for aggregates edited concurrently; migrations only through Flyway.
7. **Errors:** throw `ApiException(status, code, message)`; one `@RestControllerAdvice`; never leak exception messages/stack traces; log with correlation id.
8. **Concurrency:** immutable shared state (`record`, copy-on-write via `AtomicReference`); no `synchronized` blocks around I/O (virtual-thread pinning); every cache/map is bounded and has eviction; inject `Clock`/`LongSupplier` for time-dependent logic.
9. **Resilience:** every outbound call has connect/read/overall timeouts; retries only for idempotent calls with backoff + jitter; bounded queues; fail closed for security, fail static for config.
10. **Logging:** SLF4J, parameterised messages, no secrets/payloads/PII, levels: ERROR actionable, WARN degraded, INFO lifecycle, DEBUG off in prod.
11. **Naming:** packages `io.apiplatform.<module>.<layer>`; classes `XxxService`, `XxxController`, `XxxRepository`, `XxxProperties` (`@ConfigurationProperties` records), tests `XxxTest` (unit) / `XxxIT` (integration); constants `UPPER_SNAKE`.
12. **Configuration:** `@ConfigurationProperties` records with sane defaults, environment overrides, no secrets in YAML (placeholders resolved from env), profile `local` for demo only.
13. **Tests:** AssertJ; one behaviour per test with descriptive names; no sleeps where a fake clock exists; every bug fix has a regression test.
14. **Dependencies:** BOM-managed versions; explicit pins documented in the root `pom.xml`/ADR; run `-Pstatic-analysis` before PR.
15. **Comments:** explain *why* (invariants, trade-offs, security rationale), not *what*.
