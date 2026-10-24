# Angular review module

Output format as in `java-spring-review.md`. Verify with `npm run lint`, `npm test -- --watch=false --browsers=ChromeHeadless`, `npm run build`, and Playwright for flows.

- **Structure:** standalone components, `OnPush`, lazy routes, small components, `core/` vs `features/` vs `shared/`.
- **State/async:** signals for view state, RxJS only for streams; every subscription ends (`takeUntilDestroyed`, `async`, `toSignal`, or completes); stale responses cannot overwrite newer ones (`Loader`/`switchMap`).
- **Forms:** typed reactive forms; validators mirror server rules; errors after touch; submit disabled while in flight; idempotency key on create forms.
- **HTTP/security:** calls only via `ApiService`; interceptor adds token to same-origin API only; no secrets in the bundle; no `innerHTML`/`bypassSecurityTrust*`; route guards are UX only; error text rendered via `describeError`; token handling matches `docs/security.md`.
- **UX:** loading, empty and error states; paginated lists; accessible names (`aria-label`, labels, landmarks), keyboard focus, responsive layout (sidenav overlay < 900 px); no color-only meaning.
- **Performance:** `track` in `@for`, debounce filters, avoid heavy template functions, lazy chunks stay lazy (check build output sizes: initial budget 500 kB warn / 1 MB error).
- **Tests:** `HttpTestingController` for services, DOM-based component tests, e2e for cross-page flows, no sleeps.

**Typical AI failure modes:** NgModule/legacy APIs mixed with standalone, `subscribe` in constructors without teardown, `any` types, template-driven forms with manual validation, role checks used as security, hard-coded base URLs, `localStorage` for tokens, missing `track`, tests that assert implementation details only.
