# 001 - Task decomposition *(retrospective)*

| Task | Role | Files | Depends on | Acceptance test |
|---|---|---|---|---|
| T1 Validator + properties | backend | `policy/DestinationValidator`, `DestinationProperties` | - | `DestinationValidatorTest` (10 cases) |
| T2 Enforce at registration + audit | backend | `service/ApiService.addTarget` | T1 | `unsafeBackendDestinationsAreRejectedAndAudited` |
| T3 Re-validate at snapshot build | backend | `service/SnapshotService` | T1 | `fourEyesApprovalSnapshotPropagation...` (safe target published) |
| T4 Gateway guard (allowlist + addresses) | backend (gateway) | `config/DestinationGuard`, `SnapshotPoller.problem`, `ProxyService` | T1 contract | `routeWithDisallowedBackendHostIsRejectedAndNeverServed`, `partiallyInvalidSnapshotAppliesOnlyTheValidRoutes` |
| T5 UI error display | frontend | `features/api-detail.ts` | T2 | `api-detail.spec` SSRF case, Playwright `server rejects an SSRF backend target` |
| T6 Network layer | SRE | `infrastructure/k8s/40-*.yaml` | - | manifest review (not applied here) |
| T7 Docs + threat model rows | docs | `docs/security.md`, ADR-0008 | T1-T6 | review |
Ordering: T1 -> (T2, T3, T4 parallel) -> T5 -> T7; T6 independent.
