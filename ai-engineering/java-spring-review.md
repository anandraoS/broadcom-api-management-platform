# Java / Spring review module

Use when reviewing or generating Java. Output format: list findings as `[BLOCKER|MAJOR|MINOR] file:line - issue - why it matters - fix`. Verify by running tests; do not assume.

**Correctness**
- Transaction boundaries on services only; reads `readOnly`; no self-invocation of `@Transactional` methods (proxy bypass - e.g. `SnapshotService.current()` needed its own annotation).
- `@Transactional` + checked/`ApiException`: does rollback behaviour match intent (`noRollbackFor` for audited rejections)?
- JPA: lazy loading outside a transaction (`open-in-view=false`), N+1, cascade/orphanRemoval mistakes, equals/hashCode on entities, `@Version` usage, `IDENTITY` ids and batching.
- DTO records: validation annotations present, no entity exposure, no mass assignment.
- Time: UTC, injectable clock; no `new Date()` in logic under test.

**Spring specifics**
- Constructor injection, `@ConfigurationProperties` records with defaults; profile-gated beans (`@ConditionalOnProperty`) never enabled by default for dev-only features.
- Security: filter order, `permitAll` surface minimal, method security enabled, CSRF/CORS decisions documented, JWT validators include issuer and expiry.
- Actuator exposure limited to `health,info,prometheus`; readiness groups intentionally composed.

**Concurrency and resources**
- Shared mutable state? Prefer immutable + `AtomicReference`; per-key locks not global; no blocking I/O inside `synchronized` (virtual-thread pinning).
- Resources closed (try-with-resources for streams/clients); bounded buffers; timeouts on every client.

**Error handling and logging**
- No swallowed exceptions; security failures fail closed; messages without internals; correlation id in logs; no secrets/payloads.

**Tests**
- Negative paths, boundary values, concurrency where relevant; no sleeps when a fake clock exists; names describe behaviour.

**Typical AI failure modes to check:** invented Spring APIs/properties (verify against the BOM version), `@Autowired` field injection, catching `Exception` and returning defaults, string-concatenated JPQL, missing `@Transactional(readOnly)`, retry loops without budget, `static` caches, logging whole request objects.
