# React console validation (frontend-react)

Run 2026-10-17 on the same machine as `validation-report.md` (Node 24.13.1, Edge 155).

| Check | Command | Result |
|---|---|---|
| Type-check + production build | `npm run build` (`tsc -b && vite build`) | success; initial JS 191.97 kB raw / 63.15 kB gzip (after the react-router 7 upgrade); every page is a separate lazy chunk (largest: ApiDetailPage 13.96 kB) |
| Lint | `npm run lint` (ESLint 9, typescript-eslint, react-hooks rules) | clean |
| Unit/component tests | `npm test` (Vitest + Testing Library, jsdom) | 5 files, **22 tests passed** (API client 7, shell navigation 3, route guards 3, login 3, API detail + helpers 6) |
| Browser end-to-end vs the real stack | `E2E_CHANNEL=msedge npx playwright test` with `scripts/run-local.sh start` | **8 passed** - the identical spec used for the Angular console (`playwright-e2e-react-output.txt`) |

Not executed: Docker image build / compose profile `react` (no Docker); code-coverage report for the React app.

## Dependency finding found and fixed in this build (real, `npm audit`)

* **Before** (`npm-audit-react-before.txt`): the **production** dependency `react-router-dom` 6.x was flagged (2 moderate advisories: open redirect via backslash in `<Link>`/`useNavigate` GHSA-wrjc-x8rr-h8h6, and `deserializeErrors()` in SSR hydration GHSA-337j-9hxr-rhxg). Reachability: the console only navigates to fixed internal paths or numeric ids and does no SSR, so exploitability was low - but a fix existed.
* **Fix:** `react-router-dom` 6 -> 7.18.4 (one dependency bump; the v6 API surface used here is unchanged). `npm audit --omit=dev` now reports **0 vulnerabilities**.
* **Re-validation after the bump:** build passes, lint clean, 22 unit tests pass, **8/8 Playwright tests pass again** (`playwright-e2e-react-output.txt`).
* **Still open (dev tooling only, not shipped):** 6 findings in the Vitest/Vite/esbuild chain (`npm-audit-react-after.txt`; includes `tinypool`, rated critical by the advisory database). The only offered fix is `npm audit fix --force` (major test-runner upgrades). Decision: accept for now - they affect the local dev server/test runner, never the production bundle; review on the next Vite/Vitest upgrade. Register this as **EX-002** next to EX-001 in `docs/governance/vulnerability-remediation.md`.
