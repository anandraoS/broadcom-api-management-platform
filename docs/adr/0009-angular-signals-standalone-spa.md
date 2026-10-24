# ADR-0009: Angular 20 standalone components, signals for local state, RxJS for async

* Status: accepted - Date: 2026-10-17

## Decision
Standalone components, lazy `loadComponent` routes, `ChangeDetectionStrategy.OnPush`, signals for view state (`Loader<T>` exposes `data/loading/error` signals), RxJS for streams (debounced filters, polling with `timer` + `merge`, `switchMap`-style cancellation), reactive forms, functional interceptors/guards, Angular Material. Same-origin API via dev proxy / nginx so no CORS is needed. Angular 20 is used because Angular 21+ requires a newer Node than the build machine has; the upgrade path is `ng update`.

## Alternatives
NgModules (legacy structure); NgRx (global store - unnecessary for page-scoped server state); template-driven forms (weaker validation composition); React/Vue (excluded by the brief).

## Consequences
+ Small initial bundle (321 kB raw / ~91 kB transfer, rest lazy), predictable change detection, simple testability with `HttpTestingController`.
- Signals + RxJS interop needs discipline (`takeUntilDestroyed`, `Loader` cancels in-flight requests); token in `sessionStorage` is XSS-exposed (documented; PKCE + in-memory in production); Material theming relies on the system font (no external font CDN).

## Review triggers
Cross-page shared state grows (introduce a signal store); need SSR/SEO (not for an internal console); upgrade of Node enabling Angular 21.
