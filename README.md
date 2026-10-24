# Enterprise API Gateway, Security Policy and Observability Control Plane

A cloud-native **API management reference implementation**: a Spring Boot **control plane** (registry, versioned policies,
consumers, audit, incident view), a Java **gateway** (data plane) that enforces those policies, a sample backend, an
**Angular** console, and the Docker/Kubernetes/Helm/CI/observability assets to operate it - plus an AI-assisted
(specification-driven) engineering workspace and two study PDFs.

> Inspired by the problem domain of API management and observability products. It does **not** reproduce any vendor's
> proprietary implementation. All data is synthetic and all credentials in this repository are dummy local-only values.

## What is in the box

| Path | What it is |
|---|---|
| `control-plane/` | Spring Boot 3.3 / Java 21: REST + GraphQL, JPA + Flyway (MySQL), Spring Security (JWT), policy workflow, audit, telemetry ingestion, deterministic incident rules |
| `gateway/` | Java 21 gateway on Spring MVC + virtual threads: route table, API-key/JWT auth, rate limiting (fixed / sliding / token bucket, Redis Lua or local), circuit breaker, bulkhead, safe retries, SSRF guard, metrics, tracing |
| `sample-services/orders-service/` | Synthetic backend with `/slow`, `/flaky`, `/fail` endpoints for failure drills |
| `frontend/` | Angular 20 + Material: lazy routes, signals, reactive forms, interceptors, role-aware navigation, Karma + Playwright tests |
| `frontend-react/` | The same console in React 18 + Vite + TypeScript (22 Vitest tests; the same 8 Playwright e2e tests pass against it) - see `frontend-react/README.md`, ADR-0011 |
| `infrastructure/` | `k8s/` annotated manifests, `helm/api-platform` chart (docker-compose is at the repo root) |
| `observability/` | Prometheus config + alerts, Grafana provisioning and dashboard, Jaeger via compose |
| `performance/` | k6 scripts + a dependency-free `LoadProbe.java` that produced the measured numbers in `docs/performance.md` |
| `tests/` | How each test layer is run; k6 scenarios live in `performance/k6` |
| `ai-engineering/`, `specs/` | Reusable AI context/skills, spec-driven workflow, multi-agent protocol, review examples |
| `docs/` | Architecture, security/threat model, ADRs, runbooks, troubleshooting exercises, leadership, governance, traceability matrix, validation report, **both PDFs** |
| `scripts/` | `run-local.sh`, `smoke-test.sh`, `build-docs.sh`, helpers |

## Quick start without Docker (what was actually executed during development)

Requirements: Java 21, Maven 3.9+, curl. (Node 22/24 only for the Angular app.)

```bash
mvn -B -Plocal-h2 -DskipTests -DskipITs package      # builds three jars (H2 only for the local profile)
scripts/run-local.sh start                            # 2x orders-service, control plane (profile "local"), gateway
scripts/smoke-test.sh                                 # 32 end-to-end checks through the real gateway
scripts/run-local.sh stop
```

Then, for the UI: `cd frontend && npm ci && npm start` and open <http://localhost:4200>
(sign in with `admin`, `owner-orders`, `approver`, `operator` or `viewer`; password is `local-demo-password` in the local
profile - a dummy value). Swagger UI: <http://localhost:8080/swagger-ui.html>. Call the sample API through the gateway:

```bash
curl -H "X-API-Key: kdemo.local-demo-only-key-0123456789abcdef" http://localhost:8081/gw/orders/v1/orders
```

## Quick start with Docker Compose (not executed here - no Docker in the build environment)

```bash
cp .env.example .env        # replace every value
docker compose up --build   # add --profile ha for a second gateway sharing the Redis limiter
```

UI <http://localhost:4200> - gateway <http://localhost:8081> - Grafana <http://localhost:3000> - Jaeger <http://localhost:16686>

## Test commands (by layer)

| Layer | Command |
|---|---|
| Java unit tests | `mvn test` |
| Spring Boot integration tests (H2, MockMvc, real gateway on random port) | `mvn verify` |
| MySQL migration + Redis Lua tests (Testcontainers, **needs Docker**, auto-skipped otherwise) | `mvn verify` |
| Static analysis | `mvn -Pstatic-analysis compile spotbugs:check` |
| Angular lint / unit / build | `cd frontend && npm run lint && npm test -- --watch=false --browsers=ChromeHeadless && npm run build` |
| Angular end-to-end (real stack) | `scripts/run-local.sh start && cd frontend && E2E_CHANNEL=msedge npx playwright test` (omit `E2E_CHANNEL` to use Playwright's Chromium) |
| End-to-end smoke | `scripts/smoke-test.sh` |
| Load tests | `k6 run performance/k6/gateway-throughput.js` or `performance/run-probes.sh` (no k6 needed) |

The validation report (`docs/validation/validation-report.md`) lists exactly what was executed, with real results, and
what could **not** be validated in the build environment.

## Key design decisions (full list in `docs/adr/`)

Control plane / data plane split with **pull-based, eventually consistent** config; **fail closed** when config is
missing and **fail static** (last-known-good) when the control plane is down; four-eyes policy approval with append-only
audit; destination allowlist + address blocklist to prevent SSRF; rate limiting that degrades explicitly when Redis is
down; telemetry with bounded label cardinality.

## Demonstration security vs production security

The local profile uses an HS256 development identity provider, a shared gateway token and in-memory H2. Production is
specified in `docs/security.md`: OIDC issuer + JWKS, workload identity / mTLS between components, managed secrets,
network policies, TLS everywhere, signed images. Nothing in the repository requires a paid cloud account.

## Documentation index

Start with `docs/architecture.md`, then `docs/security.md`, `docs/policy-propagation.md`, `docs/rate-limiting.md`,
`docs/resilience-failure-scenarios.md`. PDFs: `docs/pdf/Broadcom_API_Management_Observability_Architecture.pdf` and
`docs/pdf/Broadcom_Senior_Engineer_Deep_Dive_and_Interview_Guide.pdf`. Job-description mapping: `docs/traceability-matrix.md`.
# broadcom-api-management-platform
