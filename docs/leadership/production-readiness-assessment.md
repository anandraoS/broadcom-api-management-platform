# Production-readiness assessment (honest status of this repository)

Legend: **Ready** = implemented and verified here; **Partial** = implemented, not fully verified; **Gap** = not done.

| Area | Status | Evidence / gap |
|---|---|---|
| Functional core (registry, policies, approvals, gateway enforcement) | Ready (reference level) | 52 control-plane tests (25 unit + 27 IT), 49 gateway tests (24 unit + 22 IT), 32-check live smoke run |
| AuthN/AuthZ | Partial | JWT + object-level + four-eyes tested; **production OIDC not integrated/tested** (dev HS256 only); consumers not team-scoped (gap) |
| SSRF/abuse controls | Ready (reference level) | allowlist at 3 layers + tests; DNS-rebinding window remains |
| Rate limiting | Partial | algorithms + concurrency tested locally; **Redis Lua scripts unverified here (no Docker)** |
| Resilience | Partial | timeouts/retries/breaker/bulkhead tested; graceful shutdown not exercised; no chaos testing |
| Data layer | Partial | Flyway on H2 verified; **MySQL migration test skipped (no Docker)**; EXPLAIN only on H2; no backup/restore drill |
| Observability | Partial | metrics names verified on a live gateway; dashboards/alerts/Jaeger **not run** (no Docker); tail sampling not configured |
| Frontend | Ready (reference level) | lint, 27 unit tests, production build, 8 Playwright e2e tests (Edge) |
| Containers/Kubernetes/Helm | Gap in verification | authored, **never built, rendered or applied** (no Docker/helm/kubectl) |
| CI/CD | Partial | pipeline written; **never run on a runner**; scans other than `npm audit` and SpotBugs not executed |
| Performance | Partial | laptop-scale LoadProbe numbers; no k6, no MySQL/Redis, no soak, no capacity model |
| Security review | Partial | threat model + checklists; no external penetration test, no secrets scanning tool run (manual grep only) |
| Operations | Partial | runbooks and troubleshooting exercises written, not drilled with an on-call team |
| Compliance/audit | Partial | append-only audit; retention plan documented, not implemented |

**Go-live blockers:** production OIDC integration; Redis/MySQL/Kubernetes validation in a real environment; backup/restore and failover drills; CI executed and branch protection enabled; load test on production-like infrastructure with agreed SLOs; consumer ownership model; secret rotation procedure for the gateway token.
