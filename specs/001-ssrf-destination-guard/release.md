# 001 - Documentation and release readiness *(retrospective)*

**Docs updated:** `docs/security.md` (T4/T5), `docs/adr/0008-ssrf-destination-allowlist.md`, `docs/api-contracts.md` (error codes), `docs/kubernetes-and-helm.md` (NetworkPolicy note).
**Rollout:** configure `ALLOWED_BACKEND_HOSTS` in control plane **and** gateway (same list); deploy control plane first (validation), then gateways (enforcement). A host missing from the *gateway* list drops the route - verify with `INVALID_CONFIG` events during rollout.
**Rollback:** revert the allowlist change (config) or the release; routes re-appear on the next snapshot poll.
**Human approvals required (not simulated):** security lead (design + review), tech lead (release go/no-go).
