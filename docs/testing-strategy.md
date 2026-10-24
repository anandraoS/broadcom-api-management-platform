# Testing strategy

## Pyramid and where each layer lives

| Layer | Scope | Tools | Location | Command |
|---|---|---|---|---|
| Unit | Pure logic: policy validation, SSRF validator, rate-limit algorithms, circuit breaker, route table, path safety, incident rules, Angular services/guards/interceptors | JUnit 5, AssertJ, Jasmine | `*Test.java`, `*.spec.ts` | `mvn test`, `npm test` |
| Spring Boot integration | Real security filter chain + JPA + Flyway on H2: authn/z, BOLA, SQLi, idempotency, optimistic locking, concurrency, GraphQL, OpenAPI, telemetry | `@SpringBootTest`, MockMvc | `ControlPlaneApiIT`, `QueryPlanIT` | `mvn verify` |
| API contract | Snapshot and telemetry contracts exercised from both ends (control plane emits, gateway parses with `ignoreUnknown`); error body shape | Jackson round trips in ITs | `ControlPlaneApiIT`, `GatewayRoutingIT` | `mvn verify` |
| Gateway routing/behaviour | Real gateway on a random port + real HTTP backend: routing, rewriting, auth matrix, 405/413/429, timeouts, retries, breaker, bulkhead, SSRF, metrics, readiness | `@SpringBootTest(RANDOM_PORT)`, JDK `HttpServer` | `GatewayRoutingIT` | `mvn verify` |
| Redis integration | Lua scripts, atomicity under concurrency | Testcontainers Redis | `RedisRateLimiterIT` | `mvn verify` (needs Docker; otherwise **skipped**) |
| Database integration | Flyway on MySQL 8 | Testcontainers MySQL | `MySqlMigrationIT` | `mvn verify` (needs Docker; otherwise **skipped**) |
| Concurrency | Limiter exactness under 64 threads; concurrent policy submissions | threads + latches | `InMemoryRateLimiterTest`, `ControlPlaneApiIT` | |
| Failure/recovery | Redis down (LOCAL/CLOSED/backoff/recovery), backend timeout/502/503, breaker open, invalid snapshot, control-plane-independent routing | fakes + failing backends | `ResilientRateLimiterTest`, `GatewayRoutingIT` | |
| Security regression | Authn/z, BOLA, SSRF, traversal, spoofed identity, key hashing, credential masking, token leakage in UI | all of the above | see `docs/security.md` | |
| UI end-to-end | Real browser against the real stack | Playwright (Edge channel or Chromium) | `frontend/e2e` | `scripts/run-local.sh start && cd frontend && npx playwright test` |
| Smoke | 32 curl checks through the running stack incl. propagation delay | bash + curl | `scripts/smoke-test.sh` | |
| Performance | See `docs/performance.md` | k6 (not run), LoadProbe (run) | `performance/` | |

## Critical cases checklist (status)

| Case | Covered by |
|---|---|
| Unknown routes | `unknownRouteReturnsStandardized404WithCorrelationId` |
| Unauthorized consumers | `apiKeyPolicyRejectsMissingWrongExpiredAndUnsubscribedKeys` |
| Expired / invalid tokens | `expiredTokenIsRejected`, `tokenFromWrongIssuerOrSignatureIsRejected`, `jwtPolicyVerifiesSignatureIssuerExpiryAndRole` |
| Invalid policy updates | `PolicyValidatorTest`, `invalidPolicyIsRejectedWithReasons` |
| Backend timeouts | `backendTimeoutReturns504` |
| Redis unavailability | `ResilientRateLimiterTest` (fake Redis); live Redis outage **not** exercised |
| Duplicate requests | `idempotencyKeyReplaysSameResponseAndRejectsDifferentBody`, telemetry replay |
| Excessive request sizes | `oversizedRequestBodyIsRejectedBeforeReachingTheBackend`, control-plane 413 |
| Unsafe backend destinations | `DestinationValidatorTest`, `unsafeBackendDestinationsAreRejectedAndAudited`, `routeWithDisallowedBackendHostIsRejectedAndNeverServed` |
| Missing correlation IDs | `missingOrMalformedCorrelationIdIsReplacedWithGeneratedOne`, `correlationIdIsGeneratedValidatedAndReturned` |
| Database failures | **Not covered** by automated tests (gap): add a Testcontainers test that stops MySQL mid-request and asserts 5xx handling + pool recovery |
| Partial telemetry outages | Shipper retry/drop logic is **not unit-tested** (gap); verify by stopping the control plane and watching `gateway_telemetry_dropped_total` |

## Rules

* A bug fix starts with a failing test (the live smoke run found two real bugs - telemetry key and `/apis` proxy prefix - each now has a regression test or e2e check).
* Tests never depend on wall-clock sleeps when a fake clock exists (limiters and breaker take a clock).
* No test needs production credentials; fixtures use dummy values.
* "Passed" is only claimed for commands that were run; skipped tests are reported as skipped (`docs/validation/validation-report.md`).
