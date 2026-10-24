# Docker, Kubernetes, Helm and cloud

> Validation status: manifests and chart were authored with schema-valid structure but **not applied to a cluster, not rendered by
> `helm`, not run through `docker compose`** in the build environment (none of docker/helm/kubectl were available). CI contains the
> commands (`helm lint`, `helm template | kubeconform`, `docker compose config`). See `docs/validation/validation-report.md` for what
> was syntax-checked locally.

## Container images

| Image | Base | Notes |
|---|---|---|
| control-plane, gateway, orders-service | `eclipse-temurin:21-jre-alpine`, multi-stage Maven build, Spring Boot **layered jars** (dependencies layer cached across builds) | non-root uid 10001, `MaxRAMPercentage=75`, `ExitOnOutOfMemoryError` (crash and restart beats a half-dead JVM), healthcheck, no shell login |
| frontend | `nginxinc/nginx-unprivileged` serving `dist` | uid 101, port 8080, CSP + security headers, `/api/` and `/graphql` proxied same-origin |

Container hardening: read-only root filesystem (writes only to `/tmp` and the snapshot cache volume), `cap_drop: ALL`, `no-new-privileges`,
seccomp `RuntimeDefault`, no secrets baked in, image scanning + hadolint in CI, digest pinning and cosign signing recommended for production.

## Docker Compose (`docker-compose.yml`)

mysql 8.4, redis 7.4 (no persistence), 2x orders-service, control-plane, gateway (+ `gateway-2` under `--profile ha` sharing Redis), frontend,
Prometheus, Grafana (provisioned datasource + dashboard), Jaeger. All secrets via `.env` with `${VAR:?}` (compose refuses to start with a
missing value). The seeded demo data uses the compose hostnames.

## Kubernetes (`infrastructure/k8s`)

| Concern | What the manifests do |
|---|---|
| Rollouts | `RollingUpdate maxUnavailable 0 / maxSurge 1`; readiness gates traffic; `startupProbe` tolerates slow JVM start |
| Probes | startup+liveness `/actuator/health/liveness`, readiness `/actuator/health/readiness` (gateway readiness also requires a loaded snapshot; Redis is excluded) |
| Graceful shutdown | `preStop sleep 10` (endpoint removal) + Spring `shutdown=graceful` 25 s < `terminationGracePeriodSeconds 40` |
| Resources | requests/limits per workload; JVM heap derives from the memory limit; HPA on CPU (gateway 3-20, control plane 2-6); PDBs |
| Config | `ConfigMap` for non-secrets; `Secret` **by reference** (`platform-secrets`, created out-of-band or synced by External Secrets / CSI driver); `secret.example` has intentionally invalid values |
| Network | default-deny `NetworkPolicy` + explicit allows: ingress -> gateway/frontend; gateway -> control-plane, Redis, orders, OTLP; control-plane -> MySQL, OTLP; backends reachable **only from the gateway**. Update the policy when registering a backend in a new namespace (a deliberate second control behind the registry allowlist) |
| Identity | one ServiceAccount per workload, `automountServiceAccountToken: false` (no Kubernetes API access needed) |
| Pod security | namespace labelled `pod-security.kubernetes.io/enforce: restricted`; `runAsNonRoot`, drop ALL caps, read-only FS |
| Storage | Only MySQL needs durable storage - use a managed service; the demo `StatefulSet` has a PVC (backup/restore plan required). Redis is intentionally ephemeral. Gateway snapshot cache is an `emptyDir` (survives container restarts, not pod reschedules) |
| Ingress | TLS (cert-manager annotation example), `ssl-redirect`, body-size and rps limits; separate hosts for the data plane (`/gw/`) and the console |

## Helm (`infrastructure/helm/api-platform`)

One generic workload template iterates over `components` (controlPlane, gateway, frontend, ordersService) producing ServiceAccount, Deployment,
Service, PDB and HPA; plus ConfigMap (values templated with `tpl`), Ingress and NetworkPolicies. `checksum/config` annotation rolls pods when
configuration changes. Secrets are never values: `global.existingSecret`.

Deployment flow: CI builds and scans images -> `helm lint` + `helm template | kubeconform` -> package chart `1.0.0` -> `helm upgrade --install
api-platform infrastructure/helm/api-platform -n api-platform -f values-<env>.yaml --atomic --timeout 5m` (`--atomic` rolls back automatically
if readiness fails) -> smoke test -> promote. Manual rollback: `helm rollback api-platform <rev>`. Database migrations run **before** the new
version in a separate Job (expand/contract: old and new app versions must both work against the intermediate schema); this repo runs Flyway at
application start for simplicity - move it to a pre-install hook Job when running multiple replicas with strict change control.

## Cloud mapping (primary: **AWS**; local substitutes noted)

| Capability | AWS (primary example) | Azure | GCP | Local substitute / limitation |
|---|---|---|---|---|
| Kubernetes | EKS (managed node groups/Karpenter) | AKS | GKE | kind/minikube/Docker Desktop - no cloud LB, single node |
| MySQL | Aurora MySQL / RDS Multi-AZ, IAM auth or Secrets Manager rotation | Azure Database for MySQL Flexible Server | Cloud SQL for MySQL | MySQL container / H2: no HA, no automatic backups |
| Redis | ElastiCache for Redis (cluster mode off, Multi-AZ replica) | Azure Cache for Redis | Memorystore | Redis container: no failover semantics |
| Ingress / TLS | ALB Ingress Controller + ACM certificates (or NGINX + cert-manager) | Application Gateway + Key Vault certs | GKE Gateway/Ingress + managed certs | NGINX ingress with self-signed certs |
| Secrets | Secrets Manager / SSM Parameter Store via External Secrets Operator or Secrets Store CSI | Key Vault + CSI | Secret Manager + CSI | `.env` / kubectl secrets (not production-grade) |
| Images | ECR (immutable tags, scan on push, cosign) | ACR | Artifact Registry | local daemon |
| Observability | Amazon Managed Prometheus + Grafana, X-Ray/OTel Collector to Jaeger/Tempo | Azure Monitor managed Prometheus/Grafana | Managed Service for Prometheus, Cloud Trace | Prometheus/Grafana/Jaeger containers (no long-term storage) |
| Identity for workloads | IRSA (IAM Roles for Service Accounts) | Workload Identity | Workload Identity Federation | none |
| Edge protection | CloudFront + WAF + Shield | Front Door + WAF | Cloud Armor | none |
| CI/CD | GitHub Actions OIDC -> AWS role (no long-lived keys) | OIDC federated credentials | Workload Identity Federation | Actions without deploy stage |

Equivalence is **not exact**: managed services differ in failover timing, parameter limits, networking defaults and quotas - validate failure
behaviour (Redis failover, DB failover) in the target cloud, do not assume it matches the local containers.
