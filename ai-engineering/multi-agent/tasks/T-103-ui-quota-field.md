# T-103: Add the quota field to the policy editor  *(ILLUSTRATIVE sample task - not executed)*

- **Spec:** `specs/feature-quota-policy.md` (AC 3). **Role:** R4 Angular. **Depends on:** T-101 contract frozen (field names, validation range).
- **Context:** `project-context.md`, `angular-review.md`.
- **Inspect first:** `frontend/src/app/features/api-detail.ts` (`policyForm`, `buildConfig`), `core/models.ts`, `api-detail.spec.ts`.
- **Files you may change:** those three files + `frontend/e2e/control-plane.spec.ts` (one added case).
- **Acceptance:** numeric control with min/max validators mirroring the server; empty = no quota; included in the dry-run and submit payloads; the server's `422` message is displayed.
- **Constraints:** UI validation is a convenience - never skip server validation; no new dependency.
- **Evidence:** `npm run lint`, `npm test -- --watch=false --browsers=ChromeHeadless`, `npm run build`, Playwright run summary.
