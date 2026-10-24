# Review examples (flawed -> risk -> corrected -> regression test)

The "flawed" snippets are *representative of typical generated output*, written for teaching; they were not captured from a particular model run.

## 1. SQL injection through string-built search
```java
// FLAWED
@Query(value = "select * from api_definition where name like '%" + ":q" + "%'", nativeQuery = true)   // or: em.createQuery("... '" + q + "'")
List<ApiDefinition> search(String q);
```
**Risk:** user input becomes SQL (`' OR '1'='1`, `'; DROP TABLE ...`). **Fix:** parameterised JPQL with `lower(concat('%', :q, '%'))` and optional parameters (`ApiDefinitionRepository.search`); sort fields whitelisted (`ApiController.search`). **Regression:** `ControlPlaneApiIT.searchTermIsParameterisedAgainstSqlInjection`, `searchPaginatesAndWhitelistsSortField`.

## 2. Insecure authorization (role check without object check - BOLA)
```java
// FLAWED
@PreAuthorize("hasRole('API_OWNER')")
@PostMapping("/{id}/lifecycle")
public ApiView lifecycle(@PathVariable Long id, @RequestBody LifecycleRequest r) { return service.transition(id, r.target()); }
```
**Risk:** any API owner changes any team's API by guessing ids (OWASP API1). **Fix:** `AccessGuard.assertCanModify(api)` in the service layer for REST and GraphQL; owners are scoped by `team` claim. **Regression:** `ownerCannotModifyAnotherTeamsApi_BOLA`, `ownerCannotCreateApiForAnotherTeam`.

## 3. Unbounded retries that repeat unsafe operations
```java
// FLAWED
for (int i = 0; i < 10; i++) {                      // all methods, no backoff, no budget
    try { return http.send(req, ofString()); } catch (IOException e) { /* retry */ }
}
```
**Risk:** duplicated POSTs (double charge), 10x load on a failing dependency (retry storm), unbounded latency. **Fix:** `ProxyService.forward`: safe methods only, policy flag, <= 3 attempts, exponential backoff with full jitter, inside the end-to-end timeout budget, circuit breaker. **Regression:** `safeMethodsAreRetriedOnTransientFailureButPostIsNot`, `retriesAreBoundedByAttemptLimit`, `circuitBreakerOpensAndGatewayDegrades...`.

## 4. Missing timeouts and a non-atomic distributed limiter *(code + design)*
```java
// FLAWED
Long count = redis.opsForValue().increment(key);        // default Lettuce timeout = 60 s
if (count == 1) redis.expire(key, 60, SECONDS);         // crash between the two calls leaves a key that never expires
if (count > limit) return reject();                      // Redis down -> exception -> 500 for every request
```
**Risk:** thread stall for 60 s per request when Redis is slow, keys without TTL, 500s when Redis is down, no policy for accuracy. **Fix:** one atomic Lua script per decision (`RedisRateLimiter`), 200 ms client timeouts, `ResilientRateLimiter` with 5 s back-off and an explicit fail mode (LOCAL scaled / CLOSED), Redis excluded from readiness. **Regression:** `ResilientRateLimiterTest` (5 tests); `RedisRateLimiterIT` for atomicity (needs Docker - skipped without it).

## 5. SSRF through a user-supplied URL
```java
// FLAWED
URL u = new URL(request.getTargetUrl());              // file://, http://169.254.169.254, redirects followed
return new String(u.openStream().readAllBytes());
```
**Risk:** internal network scanning, cloud-credential theft, local file read. **Fix:** `DestinationValidator` (scheme, allowlist, blocked ranges, no userinfo/query), re-validated at snapshot build and by the gateway `DestinationGuard` at connect, redirects off. **Regression:** `DestinationValidatorTest` (10 attack strings), `unsafeBackendDestinationsAreRejectedAndAudited`, `routeWithDisallowedBackendHostIsRejectedAndNeverServed`.

## 6. Secrets in logs/audit
```java
// FLAWED
log.warn("Rejected target {}", request.getUrl());                 // https://user:p4ss@host/...
audit.record("TARGET_REJECTED", ..., "url=" + request.getUrl());
```
**Risk:** credentials persisted in logs and the audit table. **Fix:** `ApiService.safeUrl` masks `//user:pass@`, key plaintext never logged or listed, API keys hashed at rest. **Regression:** `unsafeBackendDestinationsAreRejectedAndAudited` asserts `pw@` is absent from the audit response; `apiKeyIsShownOnceStoredHashed...` asserts the list view never contains the key.

## 7. Race condition: check-then-act on version numbers
```java
// FLAWED
int next = policies.maxVersionNo(apiId) + 1;       // two requests read the same max
policies.save(new PolicyVersion(api, next, ...));   // duplicate versions or silent overwrite
```
**Risk:** duplicate/ambiguous version numbers break history and revert. **Fix:** DB unique key `(api_id, version_no)` makes the collision an error; `DataIntegrityViolationException` maps to `409` for the client to retry; history stays consistent. **Regression:** `ControlPlaneApiIT.concurrentPolicySubmissionsYieldUniqueVersionsAndNeverServerErrors` (12 concurrent submits: only 201/409, no duplicates). *(Further hardening: retry once server-side or lock the API row.)*

## 8. Architectural boundary violation *(design)*
Generated: gateway imports `PolicyConfig` and repositories from the control-plane module "to avoid duplicating the model". **Risk:** the data plane inherits the control plane's dependencies, database access and release cadence; breaks independent deploys and contract versioning. **Fix:** separate mirror record in the gateway (`config/Snapshot`), JSON contract in `docs/api-contracts.md`, unknown fields ignored. **Guard:** architecture rule 13; Maven modules have no dependency on each other (a build failure would show an illegal import).

## 9. Inefficient query and N+1 / unbounded read
```java
// FLAWED
List<AuditEvent> all = auditRepo.findAll();                    // loads the whole table, filters/sorts in Java
return all.stream().filter(...).sorted(...).limit(25).toList();
```
**Risk:** memory blow-up, pool exhaustion, slow responses as data grows. **Fix:** parameterised filtered query + `Pageable` (max 100) + composite index `(entity_type, entity_id, ts)` added by migration V2 after plan review. **Regression:** `QueryPlanIT.hotQueriesUseTheirIndexes` (asserts index use), `searchPaginatesAndWhitelistsSortField`.

## 10. Unsafe configuration change *(design)*
Generated: "apply the new snapshot immediately, replacing the route table in place as routes arrive". **Risk:** half-applied config, an invalid route breaking all traffic. **Fix:** validate everything first, build an immutable table, swap atomically, keep last-known-good, reject a snapshot with no valid routes. **Regression:** `invalidSnapshotDoesNotReplaceTheLastKnownGoodConfiguration`, `partiallyInvalidSnapshotAppliesOnlyTheValidRoutes`.

## 11. Inadequate tests (tests that mirror the implementation)
```java
// FLAWED: asserts what the code does, not what must be true
assertEquals(limiter.check(...).remaining(), expectedFromSameFormulaInTheTest);
```
**Risk:** passes when the algorithm is wrong; never fails. **Fix:** behavioural tests with independent oracles - e.g. 64 threads must admit *exactly* the limit; boundary-burst test demonstrates the fixed-window weakness and the sliding-window fix; mutate the code to confirm failure. **Regression:** `InMemoryRateLimiterTest.concurrentCallersNeverExceedTheLimit`, `slidingWindowRemovesTheBoundaryBurst`.

## 12. Over-engineering
Generated: `RateLimiterStrategyFactory`, `AlgorithmRegistry`, `@RateLimiterType` annotation scanning, SPI loading for three algorithms. **Risk:** indirection hides a 20-line `switch`, more failure modes, harder tests. **Fix:** an enum + `switch` expression in `InMemoryRateLimiter`/`RedisRateLimiter`; abstraction only when a fourth implementation with different dependencies appears (stated review trigger). **Guard:** architecture rule 15 and the code-review checklist item "Simplicity".
