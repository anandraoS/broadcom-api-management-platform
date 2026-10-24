# Validation report

**Date of runs:** 2026-10-17. **Machine:** Windows 11 laptop (8 logical CPUs), Java 21.0.7 (GraalVM), Maven 3.9.12, Node 24.13.1, Microsoft Edge 155 (used as the Chromium for Karma and Playwright).
**Rule applied:** a check is reported as passed only if its command was executed in this session and exited successfully. Raw outputs live next to this file (`docs/validation/*.txt`).
**Not available in this environment:** Docker, kubectl, helm, k6, a CI runner, a real Redis/MySQL, an OIDC provider, poppler (PDF renderer).

## 1. Executed and passed

| # | Check | Command | Result |
|---|---|---|---|
| 1 | Java compilation + unit + integration, all modules | `mvn -B clean verify` (final run) | **BUILD SUCCESS** (control-plane 1:05, gateway 0:27, orders-service 0:03) |
| 2 | Control-plane unit tests | included above | 25 run, 25 passed (PolicyValidator 10, DestinationValidator 10, IncidentRules 5) |
| 3 | Control-plane integration tests (H2 in MySQL mode, MockMvc, real security filter chain) | included above | `ControlPlaneApiIT` 26 passed; `QueryPlanIT` 1 passed; `MySqlMigrationIT` **1 skipped (needs Docker)** |
| 4 | Gateway unit tests | included above | 24 run, 24 passed (rate limiter 8, resilient limiter 5, circuit breaker 4, route table/IP 5, path safety 2) |
| 5 | Gateway integration tests (real gateway + real HTTP backend) | included above | `GatewayRoutingIT` 22 passed; `RedisRateLimiterIT` **2 skipped (needs Docker)** |
| 6 | Sample service | included above | 3 passed |
| 7 | Static analysis | `mvn -Pstatic-analysis compile spotbugs:check` | 0 bug instances in all 3 modules at threshold High / effort Max (`spotbugs-output.txt`) |
| 8 | Angular lint | `npm run lint` | All files pass |
| 9 | Angular unit tests + coverage | `ng test --watch=false --browsers=ChromeHeadless --code-coverage` (CHROME_BIN = Edge) | 27 passed; coverage statements 71.5%, branches 75.3%, functions 52.1%, lines 76.6% |
| 10 | Angular production build | `npm run build` | success; initial bundle 321.23 kB raw / 90.53 kB transfer, features lazy-loaded |
| 11 | Live stack: gateway routing, authN/Z, rate limiting, policy workflow, SSRF rejection, propagation, audit, incident rules, metrics | `scripts/run-local.sh start` + `scripts/smoke-test.sh` (H2 profile, 2 sample backends) | **32 checks passed, 0 failed**; approve -> first enforced 429 observed after **5.9 s** (2 s poll) (`smoke-test-output.txt`) |
| 12 | Gateway -> control-plane telemetry shipping | after the smoke run, `GET /api/v1/traffic/summary` | real routes `orders/v1` (20 requests) and `unmatched` (1) present alongside seeded synthetic routes (`telemetry-shipping-evidence.txt`) |
| 13 | Browser end-to-end against the real stack | `npx playwright test` (Edge channel, dev server + local control plane) | 8 passed (`playwright-e2e-output.txt`) |
| 14 | Gateway routing, authN/Z (also covered in 2/5/11/13) | see tests | pass |
| 15 | Query plans | `QueryPlanIT` | hot queries use `idx_api_owner_lifecycle`, `idx_bucket_route_time`, `idx_audit_entity_ts` (**H2 plans, not MySQL**) (`query-plans-h2.txt`) |
| 16 | Load measurements | `performance/*.sh` + `LoadProbe.java` | measured, relative only (see `docs/performance.md`, `perf-*.txt`) |
| 17 | Dependency scanning (npm) | `npm audit` / `npm audit --omit=dev` | production deps: **0 vulnerabilities**; dev tooling: 14 high (one advisory chain via `braces`), no non-breaking fix; accepted with owner/expiry (`npm-audit-*.txt`, `docs/governance/vulnerability-remediation.md`) |
| 18 | Dependency upgrade | `java-jwt` 4.4.0 -> 4.6.2, `mvn -pl gateway verify` before/after | both green (`java-jwt-upgrade-evidence.txt`); performance not re-measured |
| 19 | YAML syntax | js-yaml over compose, CI workflow, Prometheus, k8s manifests, Helm Chart/values, Spring configs | 19 files parse (`yaml-syntax-check.txt`); Grafana dashboard JSON parses. **Helm templates themselves cannot be parsed as YAML and were not rendered** |
| 20 | Metric names used in dashboards/alerts | `scripts/list-metrics.sh` on live gateway and control plane | names exist (counters appear after first increment) |
| 21 | Accidental secrets | grep for private keys, cloud keys, GitHub/Slack tokens, JWT-shaped strings, `password/secret/token = <12+ chars>` | no real secret patterns; only dummy local/test values (`local-demo-*`, `test-only-*`, `change-me-*`, `kdemo...`) |
| 22 | PDFs | `scripts/build-docs.sh all`; pages rendered through Edge's PDF viewer and inspected | Architecture PDF 28 pages (91 KB), Interview guide 36 pages (122 KB; 122 questions, 53 scenario-based); mandatory two-line footer on every page; selected pages (cover, TOC, diagrams, Q&A, mind map) inspected visually - not every page |
| 23 | API documentation | `ControlPlaneApiIT.openApiDocumentIsPublished` | `/v3/api-docs` served; Swagger UI path configured |

## 2. NOT executed (authored and reviewed only)

| Area | Why | Where it would run |
|---|---|---|
| `docker build` of the 4 images, `docker compose config/up` | No Docker | CI job `images`, `kubernetes` (compose config) |
| `helm lint`, `helm template`, `kubeconform`, `kubectl apply` | No helm/kubectl/cluster | CI job `kubernetes` |
| GitHub Actions workflow | No runner | first push to GitHub |
| Trivy fs/image scans, CycloneDX SBOM, CodeQL, hadolint, Maven dependency scan | Tools unavailable/not run | CI jobs `dependency-scan`, `images`, `static-analysis` |
| Testcontainers tests: `MySqlMigrationIT`, `RedisRateLimiterIT` (Redis Lua scripts) | No Docker (3 test methods skipped) | `mvn verify` on a Docker host |
| k6 scripts (5 scenarios) | k6 not installed | `k6 run performance/k6/*.js` |
| Grafana, Prometheus, Jaeger, OTLP export | No Docker | compose stack |
| Real OIDC provider; production TLS/mTLS | None available | Phase 2 in the roadmap |
| MySQL performance, Redis latency, soak/chaos tests, backup/restore drills | No infrastructure | staging |
| Multi-agent execution | Not performed; design and samples only | n/a |
| Token-usage measurements for context modules | Not performed (method documented) | n/a |

## 3. Defects found by running things (all fixed, each with a guard)

| # | Found by | Defect | Fix / guard |
|---|---|---|---|
| 1 | first IT run | Gateway snapshot built outside a transaction (`@Transactional` self-invocation) -> `LazyInitializationException` | annotate `current()`; `fourEyesApprovalSnapshotPropagationAndRevert` |
| 2 | first IT run | GraphQL test did not dispatch the async result | test fixed |
| 3 | live seed on startup | telemetry unique key `(batch, route)` rejected multi-minute batches (also would have silently dropped data in ingestion) | key now `(batch, route, minute)`; `batchWithSeveralMinutesOfTheSameRouteStoresEveryMinute` |
| 4 | final full run | un-truncated `bucketStart` never matched its millisecond-precision stored value, so replayed batches hit the unique key (409) | normalise to the minute; `telemetryIngestionIsIdempotentAndFeedsIncidentRules` |
| 5 | Playwright | dev-server proxy prefix `/api` captured the SPA route `/apis` | proxy `/api/`; nginx `location /api/`; e2e test `unauthenticated users are redirected...` |
| 6 | smoke test | curl normalised `%2e%2e` before sending (test tool issue) | `--path-as-is` |
| 7 | YAML parse check | `ci.yml` flow-mapping with `${{ }}` was invalid YAML | block mappings; `yaml-syntax-check.txt` |
| 8 | rate-limiter unit test | sliding-window expectation was off by epsilon (algorithm correct) | test asserts the real property (<= 1 admitted at the boundary) |
| 9 | perf setup | policy validator capped rate at 100,000/min, too low for load tests | cap raised to 6,000,000/min (documented as a sanity bound) |

## 4. Known limitations and honest caveats

* Performance numbers come from one shared-CPU laptop and are relative only (`docs/performance.md`). No k6 results, no MySQL/Redis numbers, no capacity claim.
* Query plans were produced on H2; MySQL `EXPLAIN` is a release-checklist item.
* UI tests ran on Edge (Chromium) rather than Chrome; Playwright used the Edge channel.
* Docker/Kubernetes/Helm/CI assets are untested designs - expect small fixes on first execution.
* Test gaps listed in `docs/testing-strategy.md` (DB failure mid-request, telemetry shipper retry/drop, graceful shutdown).
* Consumers are not team-scoped (documented gap); production OIDC integration is not implemented/tested.
* The "multi-agent" material is a designed protocol with illustrative samples; one assistant session did all work sequentially.

## 5. Reproduce

```bash
mvn -B clean verify                                   # Java: unit + integration (Docker tests auto-skip)
mvn -B -Pstatic-analysis compile spotbugs:check       # static analysis
mvn -B -Plocal-h2 -DskipTests -DskipITs clean package && scripts/run-local.sh start && scripts/smoke-test.sh && scripts/run-local.sh stop
cd frontend && npm ci && npm run lint && npm test -- --watch=false --browsers=ChromeHeadless && npm run build
cd frontend && E2E_CHANNEL=msedge npx playwright test    # with the local stack running
scripts/build-docs.sh all                             # regenerate PDFs
```
