# Troubleshooting exercises

Ten realistic exercises. Each lists symptoms, initial hypotheses, evidence to collect, commands, metrics/logs, root-cause analysis, corrective
action, regression tests and preventive measures. **Honesty note:** these are *exercises with synthetic root causes* so a team can practise;
none of them describes a production incident. Where the sample stack can reproduce the symptom, the "Reproduce" line says how
(`orders-service` offers `/slow?ms=`, `/flaky?failPercent=`, `/fail`). Commands assume the local stack (`scripts/run-local.sh start`) or the
Kubernetes namespace `api-platform`.

| # | Exercise | Reproducible locally |
|---|---|---|
| 01 | Intermittent 502/504 | yes (`/flaky`, `/slow`) |
| 02 | Latency up after a release | partly (`/slow`) |
| 03 | Redis rate limiting inconsistent | partly (compose `--profile ha`) |
| 04 | MySQL pool exhaustion | partly (lower `DB_POOL_MAX`) |
| 05 | High JVM memory | conceptual + commands |
| 06 | Excessive GC | conceptual + commands |
| 07 | Broken policy deployment | yes (policy workflow) |
| 08 | Incorrect trace correlation | partly (Jaeger in compose) |
| 09 | Pods restarting repeatedly | Kubernetes only |
| 10 | Dependency upgrade regression | yes (real upgrade documented in `docs/governance/vulnerability-remediation.md`) |
