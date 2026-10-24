# Tests - where they live and how to run each category

Tests sit next to the code they verify (standard Maven/Angular layout); this folder is the **index**. Full strategy: `docs/testing-strategy.md`.

| Category | Location | Exact command (repo root unless noted) |
|---|---|---|
| Java unit tests | `*/src/test/java/**/*Test.java` | `mvn test` |
| Spring Boot integration tests (H2, MockMvc) | `control-plane/src/test/java/.../ControlPlaneApiIT.java`, `QueryPlanIT.java` | `mvn -pl control-plane verify` |
| Gateway routing / behaviour (real backend) | `gateway/src/test/java/.../proxy/GatewayRoutingIT.java` | `mvn -pl gateway verify` |
| API contract (snapshot + telemetry + errors) | assertions inside the two ITs above | `mvn verify` |
| Authentication / authorization / security regression | `ControlPlaneApiIT`, `GatewayRoutingIT`, `DestinationValidatorTest`, `PathSafetyTest` | `mvn verify` |
| Rate-limit tests | `gateway/.../InMemoryRateLimiterTest.java`, `ResilientRateLimiterTest.java` | `mvn -pl gateway test` |
| Policy validation | `PolicyValidatorTest`, `ControlPlaneApiIT.invalidPolicy...` | `mvn -pl control-plane test` |
| Redis integration (Testcontainers, needs Docker) | `gateway/.../RedisRateLimiterIT.java` | `mvn -pl gateway verify` (skipped without Docker) |
| Database integration (Testcontainers MySQL, needs Docker) | `control-plane/.../MySqlMigrationIT.java` | `mvn -pl control-plane verify` (skipped without Docker) |
| Concurrency | `InMemoryRateLimiterTest`, `ControlPlaneApiIT.concurrentPolicySubmissions...` | `mvn verify` |
| Failure and recovery | `ResilientRateLimiterTest`, `GatewayRoutingIT` (timeout, 502, retries, breaker, bulkhead, bad snapshot) | `mvn verify` |
| Static analysis | SpotBugs profile | `mvn -Pstatic-analysis compile spotbugs:check` |
| Angular lint | `frontend/eslint.config.js` | `cd frontend && npm run lint` |
| Angular unit tests | `frontend/src/**/*.spec.ts` | `cd frontend && npm test -- --watch=false --browsers=ChromeHeadless` (set `CHROME_BIN` to Chrome/Edge if needed) |
| Angular production build | | `cd frontend && npm run build` |
| End-to-end (UI + real stack) | `frontend/e2e/control-plane.spec.ts` | `scripts/run-local.sh start && cd frontend && E2E_CHANNEL=msedge npx playwright test` |
| Live smoke test | `scripts/smoke-test.sh` | `scripts/run-local.sh start && scripts/smoke-test.sh` |
| Performance | `performance/k6/*.js`, `performance/LoadProbe.java` | `k6 run performance/k6/gateway-throughput.js` or `performance/run-probes.sh` |
| Compose / Helm / manifests | `docker-compose.yml`, `infrastructure/**` | `docker compose config -q`; `helm lint infrastructure/helm/api-platform`; `helm template ... | kubeconform -strict` (**not run in the build environment**) |

Windows note: the Docker-free path needs only Java 21, Maven, curl, and (for the UI) Node 22/24.
