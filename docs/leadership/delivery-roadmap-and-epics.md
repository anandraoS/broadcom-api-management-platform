# Feature delivery roadmap, epics and cross-team dependencies

## Roadmap (illustrative plan for taking this reference to a production service; durations are planning assumptions, not measurements)

| Phase | Theme | Outcomes | Exit criteria |
|---|---|---|---|
| 0 - Incubation (2 wks) | Problem framing | Personas (API owner, approver, operator, consumer), assumptions list, ADR-0001..0003 drafted, walking skeleton (this repo's `local` profile) | Stakeholder review of scope and non-goals |
| 1 - MVP (6 wks) | Safe routing | Registry, versioned policies with approval, gateway enforcement, audit, dashboards | Smoke + e2e green, threat model reviewed, 1 pilot API |
| 2 - Hardening (6 wks) | Operate it | OIDC integration, MySQL HA, Redis limiter in prod, Helm/GitOps, SLO alerts, runbooks, load tests on prod-like infra | Game day passed (backend/Redis/control-plane failures), SLOs agreed |
| 3 - Scale and self-service (8 wks) | Teams onboard themselves | Quota plans (spec in `specs/`), consumer portal, per-team scoping of consumers, canary policy rollout | 10+ APIs, onboarding < 1 day |
| 4 - Intelligence (ongoing) | Insight | Tail sampling, anomaly baselines (still evidence-first), optional AI summarisation clearly labelled | Measured MTTR improvement |

## Epics and task decomposition (example for Phase 1)

| Epic | Stories / tasks (each independently shippable) | Owner type |
|---|---|---|
| E1 Registry | E1-1 API CRUD + lifecycle state machine; E1-2 versions and base-path uniqueness; E1-3 backend targets + SSRF validator; E1-4 search/pagination; E1-5 audit trail | backend |
| E2 Policy workflow | E2-1 policy schema + validator; E2-2 submit/approve/reject with four-eyes; E2-3 revert as new version; E2-4 dry-run endpoint | backend + security |
| E3 Gateway | E3-1 route table + snapshot poller; E3-2 auth (API key, JWT); E3-3 limiter (3 algorithms); E3-4 proxy (timeout/retry/breaker/bulkhead); E3-5 SSRF guard; E3-6 telemetry shipper | backend |
| E4 Console | E4-1 login + shell + role-aware nav; E4-2 registry + detail; E4-3 policy editor + approvals; E4-4 consumers/keys; E4-5 dashboard, events, incidents, audit | frontend |
| E5 Platform | E5-1 Dockerfiles + compose; E5-2 Helm chart + NetworkPolicies; E5-3 CI with scans; E5-4 dashboards/alerts | SRE |
| E6 Quality | E6-1 unit/IT suites; E6-2 e2e; E6-3 perf scripts; E6-4 failure drills | all |

Slicing rule: vertical slices that can be demoed (e.g. "approve a policy and see the gateway enforce it") beat horizontal layers; every story has acceptance criteria, a security note and an observability note.

## Cross-team dependencies

| Dependency | Needed from | By when / risk | Mitigation |
|---|---|---|---|
| OIDC client + role/team claims | Identity team | Phase 2; blocker for production | Dev identity mode behind the same resource-server code; claim contract agreed early |
| MySQL HA + backups | DBA/SRE | Phase 2 | Managed service; restore test as exit criterion |
| Redis | SRE | Phase 2 | Fail-open LOCAL mode already designed; CLOSED mode where required |
| Ingress, certificates, WAF | Network/SRE | Phase 2 | cert-manager; edge rule runbook |
| Backend teams register targets and accept SSRF allowlist process | API owner teams | Phase 1 pilot | Pilot with one team; self-service allowlist request template |
| Security review (threat model, pen test) | AppSec | Before production | Early review of ADR-0005/0008; scans in CI from day one |
| Observability stack (Prometheus/Grafana/OTel collector) | Platform observability | Phase 1 | Compose stack equivalent for dev; managed in prod |
