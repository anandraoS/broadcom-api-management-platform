# 001 - Test plan and evidence *(retrospective, evidence from real runs)*

| Requirement | Test | Layer | Last result |
|---|---|---|---|
| FR1-FR5 | `DestinationValidatorTest` (metadata IP, non-http schemes, userinfo confusion `http://localhost@evil...`, query/fragment, malformed, any-local, loopback disabled) | unit | pass |
| FR6 | `ControlPlaneApiIT.unsafeBackendDestinationsAreRejectedAndAudited` (also asserts `pw@` is not in the audit trail) | integration | pass |
| FR7 | snapshot excludes unsafe targets (covered through snapshot assertions in the policy-propagation IT) | integration | pass |
| FR8 | `GatewayRoutingIT.routeWithDisallowedBackendHostIsRejectedAndNeverServed`, `partiallyInvalidSnapshotAppliesOnlyTheValidRoutes` | integration | pass |
| End to end | `scripts/smoke-test.sh` SSRF section (3 rejections) | smoke | pass (docs/validation/smoke-test-output.txt) |
| UI | `api-detail.spec` + Playwright SSRF test | unit/e2e | pass |
| **Not covered** | DNS rebinding between check and connect; IPv6 literal forms beyond JDK classification; real cluster NetworkPolicy | - | gap |
