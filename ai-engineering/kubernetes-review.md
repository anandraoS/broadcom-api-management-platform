# Kubernetes / Helm / Docker review module

Checklist (plain manifests in `infrastructure/k8s`, chart in `infrastructure/helm/api-platform`, compose at the repo root). Remember: **nothing here was applied to a cluster in the build environment** - say so when reporting.

**Workload safety:** `runAsNonRoot` + numeric uid, `readOnlyRootFilesystem`, `allowPrivilegeEscalation: false`, drop ALL capabilities, seccomp `RuntimeDefault`, `automountServiceAccountToken: false`, one ServiceAccount per workload, namespace PSA `restricted`.
**Availability:** requests and limits set (memory limit >= heap + ~25% non-heap), startup/readiness/liveness probes correct (gateway readiness requires a snapshot; Redis excluded), `RollingUpdate maxUnavailable: 0`, PDB, HPA with sensible stabilization, topology spread, `preStop` sleep + Spring graceful shutdown < `terminationGracePeriodSeconds`.
**Network:** default-deny `NetworkPolicy` + only needed flows; backends reachable only from the gateway; egress restricted; ingress TLS, body-size and rate annotations.
**Config/secrets:** ConfigMap for non-secrets; Secrets by reference (never in values/manifests); no secret in env defaults; digest-pinned images in production.
**Helm:** `helm lint`, `helm template | kubeconform -strict`, values schema/defaults safe, `checksum/config` for rollouts, `--atomic` upgrades, rollback tested.
**Docker:** multi-stage, layered jars, non-root, no shell login, healthcheck, `.dockerignore`, scanned (Trivy) and linted (hadolint); no secrets in layers.
**Compose:** required variables use `${VAR:?}`; ports bound deliberately; read-only FS and caps dropped where possible.

**Typical AI mistakes:** `latest` tags, root containers, missing `readinessProbe`, limits equal to requests of 0, secrets written into values files, `hostNetwork`, wide-open NetworkPolicies, probes hitting endpoints that need auth, forgetting `startupProbe` for JVMs.
