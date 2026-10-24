# Project context (always load; keep this file under ~80 lines)

**What:** API management platform = **control plane** (Spring Boot, MySQL, REST+GraphQL) + **gateway** (Java 21, Spring MVC, virtual threads) + **Angular** console + sample backend. Synthetic data, dummy credentials only.

**Modules and boundaries**
| Module | Purpose | May depend on | Must NOT |
|---|---|---|---|
| `control-plane/` | registry, policy workflow, consumers/keys, audit, snapshot publisher, telemetry, incident rules | Spring, JPA, Flyway | import `gateway` code; expose entities; call backends |
| `gateway/` | route table, auth, limiter, proxy, breaker, telemetry shipper | Spring Web, Lettuce, java-jwt | import `control-plane` code; query MySQL; trust inbound identity headers |
| `sample-services/orders-service/` | failure-drill backend | Spring Web | contain business logic |
| `frontend/` | Angular 20 console | Material, RxJS | hold secrets; treat guards as security |
Contract between control plane and gateway: snapshot + telemetry JSON (`docs/api-contracts.md`); additive changes only.

**Commands** (Java at `~/.jdks/graalvm-jdk-21.0.7` on the dev machine; Maven 3.9+)
`mvn test` unit - `mvn verify` unit+IT - `mvn -Pstatic-analysis compile spotbugs:check` - `mvn -Plocal-h2 -DskipTests -DskipITs package` then `scripts/run-local.sh start` and `scripts/smoke-test.sh` - frontend: `npm run lint`, `npm test -- --watch=false --browsers=ChromeHeadless`, `npm run build`, `npx playwright test`.

**Coding standards:** `docs/governance/java-coding-standards.md`, `angular-coding-standards.md`, `rest-api-standards.md`. Naming: `XxxService/Controller/Repository/Properties`, tests `XxxTest` (unit) `XxxIT` (integration).

**Security constraints (hard):** deny by default; role **and** object-level authorization; no secrets/PII in code/logs/tests/prompts; parameterised queries only; outbound URLs only from the allowlisted registry (SSRF); bounded caches/queues; every outbound call has timeouts; no retries for unsafe methods; audit privileged actions in the same transaction.

**Testing requirements:** every behaviour change has tests incl. negative/failure paths; bug fix = regression test first; skipped tests are reported as skipped; Testcontainers ITs need Docker (auto-skip otherwise).

**Architectural invariants** (see `architecture-rules.md`): control plane never in the request path; gateway serves last-known-good config; invalid config never replaces valid config; policy changes go submit -> validate -> four-eyes approve; history is append-only; metrics labels are bounded; Redis outage degrades limiting but never breaks availability.

**Definition of Done:** `docs/governance/definition-of-done.md` (tests, security checklist, observability, docs, review, rollback).

**Inspect-before-change rules**
- Policy/validation change -> read `PolicyConfig`, `PolicyValidator`, `PolicyService`, gateway `SnapshotPoller.problem()` and the snapshot contract; update both validators and tests.
- Gateway behaviour -> read `GatewayFilter` (order of steps matters), `ProxyService`, the relevant `*IT` tests.
- Auth/security -> `SecurityConfig`, `AccessGuard`, `GatewayTokenFilter`, `Authenticator`, `docs/security.md`.
- Schema -> latest `db/migration/V*.sql`, entities, `QueryPlanIT`; never edit an applied migration.
- UI -> `core/api.service.ts`, `core/interceptors.ts`, the page component and its spec.
- Anything touching metrics -> `TelemetryRecorder` (label rules) and `docs/observability.md`.

**Where things are:** `docs/architecture.md` (start here), `docs/adr/` (decisions), `ai-engineering/repo-map.md` (file map), `docs/validation/` (what was actually verified).
