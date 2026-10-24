# Release readiness checklist

| Area | Check | Evidence |
|---|---|---|
| Scope | Release notes list features, fixes, breaking changes, config changes, migrations | changelog / PR links |
| Quality | CI green on the release commit (all stages), no unexplained skipped tests, coverage trend not down | CI run URL |
| Security | Dependency + image scans reviewed (exceptions registered with owner/expiry), threat-model changes reviewed, secrets rotation not due | scan reports, `vulnerability-remediation.md` register |
| Data | Migrations reviewed (`db-migration-checklist.md`), backward compatible with N-1, backup taken/verified before deploy, restore tested recently | migration PR, backup id |
| Performance | p95/p99 and throughput vs baseline within budget; resource requests/limits validated; soak/failure test results attached | k6/LoadProbe output |
| Reliability | Health probes, graceful shutdown, PDB, HPA verified in staging; failure drill (kill backend, Redis, control plane) passed | drill notes |
| Observability | Dashboards/alerts updated, SLO burn alerts active, on-call has runbooks for new failure modes | dashboard links |
| Compatibility | API contract diff reviewed (additive only within major), gateway/control-plane snapshot compatibility confirmed (N and N-1) | contract tests |
| Rollout | Order of deployment (migrations -> control plane -> gateways -> UI), canary %, propagation window communicated, rollback command and owner named | runbook |
| Communication | Stakeholders/partners notified of changes and maintenance; support briefed | message log |
| Approval | Tech lead + security (for security-relevant changes) + product sign-off; human approval recorded for risky changes | approvals |

**Go / no-go rule:** any red item in Security, Data or Reliability blocks the release unless an exception with owner and expiry is approved by the tech lead and security reviewer. Performance misses block unless the product owner accepts the documented risk.
