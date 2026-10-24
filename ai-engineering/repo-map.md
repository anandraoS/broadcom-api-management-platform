# Repository map (use this instead of scanning the tree)

```
control-plane/src/main/java/io/apiplatform/controlplane/
  domain/        JPA entities (ApiDefinition, ApiVersion, BackendTarget, PolicyVersion, Consumer, Subscription, ApiKey, AuditEvent, IdempotencyRecord, TrafficBucket, OperationalEvent)
  repo/          Spring Data repositories (parameterised JPQL)
  policy/        PolicyConfig (record), PolicyValidator, DestinationValidator (+Properties)  <- pure, heavily unit-tested
  service/       ApiService, PolicyService, ConsumerService, SnapshotService(+GatewaySnapshot), TelemetryService, IdempotencyService, AuditService, RetentionJob
  incident/      IncidentRules (deterministic), IncidentService
  security/      SecurityConfig, AccessGuard (object-level), GatewayTokenFilter, CorrelationFilter, RequestSizeFilter, CurrentUser, *Properties
  web/           controllers, Dtos, GlobalExceptionHandler, ApiException, GraphQlController, AuthController (dev only)
  DemoDataSeeder.java  synthetic data (profile/props gated)
control-plane/src/main/resources/  application.yml, application-local.yml, db/migration/V1,V2, graphql/schema.graphqls
control-plane/src/test/            *Test (unit), ControlPlaneApiIT, QueryPlanIT, MySqlMigrationIT (Docker)

gateway/src/main/java/io/apiplatform/gateway/
  proxy/GatewayFilter   THE request pipeline (order matters)      proxy/ProxyService   timeout/retry/breaker/SSRF re-check/trace inject
  config/               Snapshot, SnapshotPoller (validate+swap+disk cache), SnapshotHolder, RouteTable, DestinationGuard, SnapshotHealthIndicator
  auth/                 Authenticator (API key/JWT), IpAllowList
  ratelimit/            RateLimiter, InMemoryRateLimiter, RedisRateLimiter (Lua), ResilientRateLimiter, RateLimiterConfig
  resilience/           CircuitBreaker, TargetSelector, HealthChecker
  telemetry/            TelemetryRecorder (metrics + minute buckets), TelemetryShipper
gateway/src/test/       unit tests, proxy/GatewayRoutingIT (real backend), RedisRateLimiterIT (Docker)

sample-services/orders-service/   /orders, /slow, /flaky, /fail, /echo
frontend/src/app/  core/(api.service, auth.service, interceptors, guards, loader, models)  shared/state-view  features/*  shell.ts  app.routes.ts ; e2e/ (Playwright)
infrastructure/    k8s/*.yaml (annotated)  helm/api-platform/   ../docker-compose.yml  ../.github/workflows/ci.yml
observability/     prometheus/(prometheus.yml, alerts.yml)  grafana/(provisioning, dashboards)
performance/       k6/*.js  LoadProbe.java  run-probes.sh  measure-with-resources.sh  prepare-perf-policy.sh
scripts/           run-local.sh  smoke-test.sh  list-metrics.sh  build-docs.sh
docs/              architecture, security, api-contracts, policy-propagation, rate-limiting, observability, resilience, database-performance, performance, protocols, k8s, ci-cd, testing,
                   adr/, governance/, runbooks/, troubleshooting/, leadership/, validation/ (raw evidence), pdf/ (generator + PDFs), traceability-matrix.md
ai-engineering/    modules, multi-agent/, review-examples/, context-examples/       specs/   feature specs
```
Regenerate this map when directories change; keep it under 60 lines so it stays cheap to load.
