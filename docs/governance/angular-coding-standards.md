# Angular coding standards

1. **Standalone components only**, `ChangeDetectionStrategy.OnPush` everywhere, lazy `loadComponent` per route, `withComponentInputBinding` for route params (`input.required<string>()`).
2. **State:** signals for local/view state; RxJS for streams (debounce, polling, cancellation). Server state per page via `Loader<T>` (loading/error/data signals; cancels the in-flight request; ends with the component via `takeUntilDestroyed`).
3. **Subscriptions:** never leave an unmanaged `subscribe` in a long-lived component - use `async`/`toSignal`, `takeUntilDestroyed`, or a one-shot HTTP call that completes.
4. **Forms:** typed reactive forms (`nonNullable`), validators mirroring server rules (the server remains authoritative), `mat-error` shown after touch, submit blocked while invalid and while a request is in flight; `Idempotency-Key` per create form instance.
5. **HTTP:** only through `ApiService` (typed); functional interceptors for auth header + correlation id and 401 handling; errors rendered via `describeError` (safe text, never `innerHTML`).
6. **Security:** no secrets or backend URLs in the bundle; the token is attached to same-origin `/api/` and `/graphql` only; route guards are UX hints, never access control; no `bypassSecurityTrust*`; no `innerHTML` with server data.
7. **Templates:** new control flow (`@if/@for` with `track`), small templates, stable `track` ids for list rendering, `aria-label`s on tables/forms/paginators, labels for every input, visible focus, role-appropriate landmarks (`banner`, `navigation`, `main`).
8. **UX states:** every data view handles **loading**, **error**, **empty** (via `<app-state>`).
9. **Styling:** Angular Material theme tokens; system font stack (no external CDNs); responsive grid (`auto-fill`), sidenav switches to overlay below 900 px.
10. **Testing:** services/interceptors/guards with `HttpTestingController`; components through the DOM (no private-API reliance beyond form setup); Playwright for cross-page flows. Lint (`ng lint`) must pass.
11. **Performance:** lazy chunks; avoid heavy pipes in templates; `track` on loops; debounce text filters (300 ms); poll with `timer` + `switchMap`-style cancellation.
12. **Naming/layout:** `core/` (services, models, interceptors), `shared/` (reusable components), `features/<page>.ts`; file names kebab-case; one component per file.
