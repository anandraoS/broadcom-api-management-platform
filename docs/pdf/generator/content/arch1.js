'use strict';
const D = require('../lib/diagrams');

module.exports = [
  // ---------------------------------------------------------------- 1
  { h1: '1. Business problem and project goals' },
  { p: 'An enterprise runs many backend services used by internal teams and external partners. Without a central platform every team re-implements authentication, throttling, routing rules, logging and change control - inconsistently - and nobody can answer basic questions such as "which APIs exist, who owns them, who may call them, what changed before the outage?". This project is a **reference implementation** of an API management and observability platform: a management **control plane**, a working **gateway (data plane)**, an Angular console, and the delivery assets to operate them.' },
  { p: 'It is inspired by the problem domain of enterprise API management and observability products and does not reproduce any vendor\'s proprietary implementation. All data is synthetic; every credential in the repository is a dummy local-only value.' },
  { h3: 'Goals' },
  { ul: ['**Register and discover APIs** with ownership, versions, lifecycle and documentation.', '**Enforce policies on live traffic**: authentication, authorization, rate limits, timeouts, methods, size limits, IP restrictions, routing.', '**Change policies safely**: validate, four-eyes approve, version, apply, audit, revert.', '**Observe and investigate**: latency, errors, throughput, rate-limit and auth failures, traces, correlated config changes.', '**Operate it cloud-natively**: Docker, Kubernetes, Helm, CI/CD, secure defaults.', '**Show disciplined AI-assisted engineering**: specs, reusable context, review gates, honest evidence.'] },
  { h3: 'Explicit non-goals' },
  { ul: ['Reproducing a commercial product, a developer portal, billing, or multi-region active-active control planes.', 'Claiming production readiness: `docs/leadership/production-readiness-assessment.md` lists the real gaps (no cluster run, no Docker validation, no live OIDC).'] },

  // ---------------------------------------------------------------- 2
  { h1: '2. Functional and non-functional requirements' },
  { table: { head: ['Area', 'Functional requirement', 'Where implemented'], widths: [1.2, 4, 2.4], rows: [
    ['Registry', 'APIs with owner team, lifecycle (DRAFT/ACTIVE/INACTIVE/DEPRECATED), versions with unique base paths, multiple weighted targets, search/filter/pagination, optimistic locking', 'ApiService, ApiDefinitionRepository, V1 schema'],
    ['Policies', 'Auth type, required role, methods, rate limit + algorithm + burst, timeout, max body, IP allowlist, retry flag; versioned; dry-run validation; submit/approve/reject/revert', 'PolicyConfig, PolicyValidator, PolicyService'],
    ['Consumers', 'Consumers, subscriptions, API keys (hashed, expiring, revocable), shown once', 'ConsumerService'],
    ['Gateway', 'Route match, rewrite, auth, limits, size/method/IP checks, timeouts, retries (safe only), breaker, bulkhead, health checks, SSRF guard, correlation ids, metrics', 'gateway module (GatewayFilter, ProxyService, ...)'],
    ['Audit', 'Append-only record of privileged actions with actor and correlation id, atomic with the change', 'AuditService, AuditEvent'],
    ['Observability', 'Request volume, error rate, latency percentiles, status distribution, rate-limit/auth failures, traces, structured logs', 'TelemetryRecorder, Prometheus, Grafana, OTLP'],
    ['Incidents', 'Deterministic rules with evidence, recent config changes, correlated events', 'IncidentRules, IncidentService, UI'],
    ['APIs', 'REST (+ RFC 7807 errors, idempotency), small read-only GraphQL, OpenAPI', 'web/ controllers, schema.graphqls'],
    ['Console', 'Login, registry, detail/versions/targets, policy editor, approvals, consumers/keys, dashboard, events, incidents, audit, role-aware nav', 'frontend/src/app'] ] } },
  { table: { head: ['Quality attribute', 'Non-functional requirement', 'How it is addressed'], widths: [1.2, 3, 3.4], rows: [
    ['Availability', 'Traffic must survive control-plane, Redis and telemetry outages', 'Fail-static snapshot (memory + disk), Redis fallback modes, bounded async telemetry'],
    ['Security', 'Deny by default, no unapproved destinations, no secrets in code, object-level authorization', 'Spring Security, AccessGuard, 3-layer destination allowlist, hashed keys, constant-time compares'],
    ['Performance', 'Hot path O(1) in routes; bounded memory; no blocking on telemetry', 'Immutable route table, per-key locks, virtual threads, bounded buffers'],
    ['Consistency', 'Config changes bounded-delay, never half-applied', 'Atomic swap, validate-then-apply, after-commit invalidation'],
    ['Operability', 'Probes, graceful shutdown, dashboards, alerts, runbooks', 'Actuator groups, preStop + drain, Grafana JSON, Prometheus rules'],
    ['Maintainability', 'Clear module boundaries, tests for every control', 'Architecture rules, 136 automated tests (101 Java, 27 Angular, 8 e2e) + a 32-check smoke script, ADRs'] ] } },

  // ---------------------------------------------------------------- 3
  { h1: '3. Overall architecture' },
  { p: 'The platform separates a low-traffic, stateful, transactional **control plane** from a high-traffic, stateless, latency-critical **data plane**. Gateways never query the database: they pull an immutable configuration snapshot, validate it, swap it atomically and keep serving the last-known-good copy if the control plane disappears.' },
  { diagram: D.component, h: 285, cap: 'Component diagram: consumers enter through the ingress to the gateway; operators use the console and control plane; configuration flows down as snapshots, telemetry flows up as batches.' },
  { ul: ['**Why two deployables?** Scale, availability and release cadence differ (ADR-0001).', '**Why pull?** Idempotent, self-healing, outage-tolerant; costs a bounded propagation delay (ADR-0003).', '**Why a custom gateway?** Registry-driven policy pipeline, full control of limits/breaker/error contract, simple tests (ADR-0002); trade-offs recorded.'] },

  // ---------------------------------------------------------------- 4
  { h1: '4. Control-plane and gateway responsibilities' },
  { table: { head: ['Concern', 'Control plane', 'Gateway'], widths: [1.4, 3, 3], rows: [
    ['State', 'MySQL (system of record), in-memory snapshot cache', 'Memory only: snapshot, breaker/health state, local limiter; optional disk copy of last-known-good'],
    ['Primary job', 'Validate, approve, version, audit and publish configuration; consumer/key lifecycle; operator read models', 'Resolve route, authenticate, authorize, limit, validate, proxy, emit telemetry'],
    ['Scaling driver', 'Operator load (low)', 'Consumer traffic (high); CPU-bound; HPA'],
    ['If it fails', 'Changes and console stop; traffic continues; revocations delayed', 'Its share of traffic stops; other replicas continue'],
    ['Security boundary', 'JWT (OIDC) for users, gateway token for /internal/**', 'API key / JWT for consumers; never trusts inbound identity headers'],
    ['Never does', 'Sit on the request path; call backends', 'Query MySQL; mutate live route tables; follow redirects'] ] } },

  // ---------------------------------------------------------------- 5
  { h1: '5. Component and deployment diagrams' },
  { p: 'Figure 1 (section 3) shows the logical components. The deployment view below shows the Kubernetes layout produced by `infrastructure/k8s` and the Helm chart: workloads are non-root with read-only root filesystems, a default-deny NetworkPolicy permits only the required flows, and backends are reachable only through the gateway.' },
  { diagram: D.deployment, h: 275, cap: 'Deployment diagram (Kubernetes): ingress, gateway, control plane, frontend, sample backend, managed data stores in production, config by reference.' },
  { warn: 'These manifests and the Helm chart were authored and reviewed but **never applied to a cluster** in the build environment (no Docker, kubectl or helm available). CI contains the validation commands (`helm lint`, `kubeconform`).' },

  // ---------------------------------------------------------------- 6
  { h1: '6. Database ER diagram' },
  { p: 'Schema source of truth: `control-plane/src/main/resources/db/migration/V1__core_schema.sql` plus `V2__audit_entity_time_index.sql` (a change driven by a query-plan review). Hibernate runs with `ddl-auto=validate`; Flyway owns the schema.' },
  { diagram: D.er, h: 330, cap: 'Entity-relationship overview (selected columns). Unique keys encode invariants: one gateway path per API version, one version number per policy history, one key prefix per key, idempotent telemetry per (batch, route, minute).' },
  { ul: ['**Optimistic locking:** `api_definition.version`; stale `PUT` returns `409 concurrent_modification`.', '**Audit:** append-only entity written in the same transaction (`Propagation.MANDATORY`); not purged by the application.', '**Retention:** telemetry 7 days, idempotency records 24 h (`RetentionJob`); audit follows a compliance-driven partition/archive plan.', '**Plan review:** `QueryPlanIT` asserts hot queries use their intended indexes (H2 plan shape only - confirm on MySQL with `EXPLAIN ANALYZE`).'] },

  // ---------------------------------------------------------------- 7
  { h1: '7. Request lifecycle from consumer to backend' },
  { p: 'The order of the gateway pipeline is deliberate: cheap, anonymous-safe checks run before expensive or stateful ones, and unauthenticated callers learn nothing beyond "not found / not allowed / unauthorized".' },
  { diagram: D.lifecycle, h: 335, cap: 'Gateway pipeline (GatewayFilter). Left: steps; right: the standardized rejection for each step. Step 10 applies timeout budget, safe retries and the circuit breaker.' },
  { table: { head: ['Proxy detail', 'Behaviour'], widths: [2, 5.4], rows: [
    ['Path rewriting', 'Base path stripped: /gw/orders/v1/items/7 -> backend /items/7 (query preserved); dot-segments rejected before forwarding'],
    ['Headers', 'Hop-by-hop removed; inbound Authorization/X-API-Key/X-Consumer-Id/X-Forwarded-* discarded; gateway sets X-Consumer-Id, X-Correlation-Id, X-Forwarded-*, traceparent'],
    ['Target choice', 'Smooth weighted round robin over healthy targets whose breaker allows traffic; none left -> 503 no_healthy_backend + Retry-After'],
    ['Timeout and retry', 'policy.timeoutMs is an end-to-end budget; retries (<=3) only for GET/HEAD/OPTIONS on connect/502/503/504 with full-jitter backoff inside the budget'],
    ['Limits', 'Request body bounded (declared and streamed); response capped at 10 MiB; per-route concurrency semaphore'] ] } },

  // ---------------------------------------------------------------- 8
  { h1: '8. Authentication and authorization flows' },
  { diagram: D.auth, h: 255, cap: 'Operator flow (JWT, role + object-level checks) and consumer flow (API key hash lookup or JWT, subscription scoping, identity asserted to the backend).' },
  { ul: ['**Operators:** Spring Security resource server validates signature, issuer and expiry; roles/team claims drive `@PreAuthorize` and `AccessGuard` (BOLA defence). The dev identity provider issues the same claims an OIDC provider would; production switches to `issuer-uri` without code changes (ADR-0005).', '**Consumers:** API keys `prefix.secret` - 256-bit random secret, only SHA-256 stored, constant-time comparison (hash computed even for unknown prefixes), expiry, revocation, subscription to the specific API; JWT alternative verified on the gateway.', '**Internal channel:** `X-Gateway-Token`, constant-time, `ROLE_GATEWAY` only on `/internal/**`; mTLS/workload identity in production.', '**Four eyes:** a submitter cannot approve their own policy (403 `self_approval`).'] },
  { warn: 'Demonstration security is not production security: dev HS256 secret, shared gateway token and HTTP on localhost. `docs/security.md` section 5 lists the production requirements.' },

  // ---------------------------------------------------------------- 9
  { h1: '9. Policy configuration and propagation' },
  { diagram: D.propagation, h: 262, cap: 'Policy approval and propagation. Delay = poll interval plus control-plane cache staleness; measured locally at about 7-8 s with a 2 s poll.' },
  { table: { head: ['Question', 'Answer'], widths: [2.4, 5], rows: [
    ['Is propagation instantaneous?', '**No.** Eventually consistent: bounded by the poll interval (default 5 s) plus up to 30 s cache staleness on other control-plane replicas. Different gateways may enforce different versions during the window.'],
    ['What if the control plane is down?', 'Gateways keep serving the last-known-good snapshot (also persisted to disk); SNAPSHOT_STALE event after 3 failed polls; no changes - including revocations - take effect until recovery.'],
    ['What if a snapshot is invalid?', 'The gateway validates every route/target; invalid routes are dropped with INVALID_CONFIG events; a snapshot with no valid routes is rejected whole and the previous config stays.'],
    ['How is a bad change undone?', 'Revert creates a new version from an older one (append-only history), audited, propagates the same way.'] ] } },

  // ---------------------------------------------------------------- 10
  { h1: '10. Rate limiting and quota enforcement' },
  { diagram: D.ratelimit, h: 185, cap: 'Resilient rate limiter: atomic Redis Lua decisions with a local fallback (LOCAL mode, divided limit) or fail-closed (CLOSED mode).' },
  { table: { head: ['Algorithm', 'Behaviour', 'Weakness', 'Evidence'], widths: [1.4, 3, 2.4, 2.6], rows: [
    ['Fixed window', 'Count per 60 s window', 'Boundary burst up to 2x', 'fixedWindowAllowsBurstAtBoundary_knownWeakness'],
    ['Sliding window counter', 'prev*(1-elapsed)+current', 'Approximation', 'slidingWindowRemovesTheBoundaryBurst'],
    ['Token bucket', 'Refill limit/60s, capacity burst', 'Two parameters', 'tokenBucketAllowsBurstThenRefills...'] ] } },
  { ul: ['**Local vs distributed:** local limiting is exact per instance but admits up to N x limit across N gateways; Redis gives a global limit at the cost of a round trip and a new dependency (see `docs/rate-limiting.md`).', '**Concurrency:** 64 threads x 50 calls admit exactly the limit locally; Lua scripts make Redis decisions atomic (Redis test needs Docker and was skipped in the build environment).', '**Daily/monthly quotas** are specified (`specs/feature-quota-policy.md`) as the sample new-policy feature but **not implemented**.'] },

  // ---------------------------------------------------------------- 11
  { h1: '11. Distributed tracing and observability' },
  { diagram: D.observability, h: 200, cap: 'Telemetry paths: pull-based metrics, OTLP traces, structured logs with correlation ids, and per-minute aggregates feeding the deterministic incident rules.' },
  { ul: ['**Cardinality:** `route` label = registered `apiSlug/version` or `unmatched`; never raw paths, consumer ids, IPs or status codes. A test proves random 404 paths do not create series.', '**Sampling and cost:** head sampling configurable (0.5 compose, 0.05 Kubernetes); tail sampling in the OpenTelemetry Collector is the recommended production upgrade (keep all errors/slow traces).', '**Redaction:** no bodies, headers or credentials in logs/traces; correlation ids validated to prevent log injection.', '**Propagation:** `traceparent` injected on the proxied call from the gateway span; correlation id forwarded and echoed.', '**Verified:** metric names were checked against live services with `scripts/list-metrics.sh`; Grafana/Prometheus/Jaeger themselves were not run (no Docker).'] },

  // ---------------------------------------------------------------- 12
  { h1: '12. Failure detection and recovery' },
  { table: { head: ['Scenario', 'Detect', 'Contain', 'Recover / prevent'], widths: [1.7, 2.2, 2.5, 2.6], rows: [
    ['Backend down', 'TARGET_UNHEALTHY, CIRCUIT_OPEN, 5xx alert', 'Breaker fast-fails; bulkhead; other APIs unaffected', 'Half-open probe, health check; >=2 targets, readiness probes'],
    ['Redis down', 'Redis failure counter + event', '5 s back-off; LOCAL (divided limit) or CLOSED (503); Redis not in readiness', 'Auto recovery; managed Redis with failover'],
    ['Slow DB query', 'Hikari pending, slow log', '3 s connection timeout; traffic unaffected (no DB on data path)', 'EXPLAIN review, index migration, rollback'],
    ['Gateway restart', 'Restart count, readiness flap', 'Graceful drain, maxUnavailable 0, PDB', 'On-disk snapshot cache; >=3 replicas'],
    ['Invalid policy', '422 at submit; INVALID_CONFIG at apply', 'Validation, four-eyes, last-known-good', 'Dry-run, contract tests, revert'],
    ['Observability backend down', 'up==0, dropped counters', 'Async bounded exporters; never blocks requests', 'Redundant collectors; independent health checks'],
    ['Retry storm', 'Retries >> requests, latency up', 'Safe methods only, <=3 attempts, jitter, end-to-end budget, breaker', 'Retry budgets (future), client backoff guidance'] ] } },
  { note: 'Full playbooks (detection, impact, containment, recovery, prevention) are in `docs/resilience-failure-scenarios.md`; ten troubleshooting exercises are in `docs/troubleshooting/`.' },

  // ---------------------------------------------------------------- 13
  { h1: '13. Docker and Kubernetes architecture' },
  { ul: ['**Images:** multi-stage builds, Spring Boot layered jars, `eclipse-temurin:21-jre-alpine`, uid 10001, `MaxRAMPercentage=75`, `ExitOnOutOfMemoryError`, healthchecks; nginx-unprivileged for the SPA (CSP, same-origin API proxy; note `/api/` with trailing slash so it does not capture the SPA route `/apis` - a bug found by an end-to-end test).', '**Compose:** MySQL, Redis, two backends, control plane, gateway (+ second gateway under profile `ha`), frontend, Prometheus, Grafana, Jaeger; secrets from `.env` with `${VAR:?}`.', '**Kubernetes:** RollingUpdate (maxUnavailable 0), startup/readiness/liveness probes (gateway readiness requires a loaded snapshot; Redis excluded), preStop 10 s + 25 s drain < 40 s grace, requests/limits, HPA, PDB, topology spread, ServiceAccount per workload without API token, PSA `restricted`, default-deny NetworkPolicies, ingress TLS example.', '**Storage:** only MySQL is durable (managed service in production); Redis is intentionally ephemeral; gateway snapshot cache is an emptyDir.', '**Cloud mapping:** AWS primary (EKS, Aurora/RDS MySQL, ElastiCache, ALB+ACM, Secrets Manager via External Secrets, IRSA, ECR, managed Prometheus/Grafana) with Azure and GCP equivalents and local substitutes documented in `docs/kubernetes-and-helm.md`.'] },

  // ---------------------------------------------------------------- 14
  { h1: '14. Helm deployment flow' },
  { diagram: D.helmflow, h: 100, cap: 'Helm delivery flow: images are built and scanned in CI, the chart is linted and rendered against schemas, then upgraded atomically so a failing readiness check rolls the release back.' },
  { ul: ['One generic template iterates over `components` (controlPlane, gateway, frontend, ordersService) producing ServiceAccount, Deployment, Service, PDB and HPA; separate templates create ConfigMap, Ingress and NetworkPolicies.', '`checksum/config` annotation rolls pods when configuration changes; secrets are referenced via `global.existingSecret`, never stored in values.', '`helm rollback api-platform <revision>` reverts; database migrations are expand/contract so N-1 and N coexist (move Flyway to a pre-install Job for strict change control).', 'Status: chart reviewed only; `helm lint`/`template` not executed locally (no helm binary).'] },
];
