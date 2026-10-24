# React console (alternative to the Angular console)

Same product, same backend, same features as `../frontend` (Angular 20) - written in **React 18 + TypeScript + Vite**. Both consoles talk to the control-plane REST API only; neither holds secrets, and backend authorization stays authoritative (route guards and hidden menu items are UX only).

| Concern | Angular console | React console |
|---|---|---|
| Structure | standalone components, lazy `loadComponent` | function components, `React.lazy` + `Suspense` per page |
| State | signals + `Loader<T>` | `useAsync` hook (abort previous request, abort on unmount) + local `useState` |
| HTTP | `HttpClient` + functional interceptors | one `fetch` wrapper (`core/api.ts`): bearer token for same-origin `/api/` only, correlation id, 401 handler, RFC 7807 -> safe messages |
| Forms | reactive forms + validators | controlled inputs; validators mirror the server rules (server stays authoritative) |
| Auth | `AuthService` (sessionStorage) | `AuthProvider` context (sessionStorage) |
| Routing/guards | Angular Router + `CanActivate` | React Router 6 + `RequireAuth` / `RequireRole` |
| UI kit | Angular Material | plain CSS (no component library) |
| Tests | Karma/Jasmine (27) | Vitest + Testing Library (22) |
| End-to-end | Playwright (8) | **the same Playwright spec** (8) - identical accessible names and roles |

```bash
cd frontend-react
npm ci
npm run lint && npm test && npm run build        # static checks, 22 unit tests, production build
scripts/run-local.sh start                        # (repo root) local stack on :8080/:8081
npm start                                         # React dev server on http://localhost:4300 (proxies /api/ to :8080)
E2E_CHANNEL=msedge npx playwright test            # 8 browser tests (omit E2E_CHANNEL to use Playwright's Chromium)
```

Docker: `docker compose --profile react up --build` serves it on <http://localhost:4300> (not built in the build environment - no Docker).
Security notes are the same as for Angular: the access token is kept in `sessionStorage` (XSS-readable - production uses authorization-code + PKCE with an in-memory token); React escapes all rendered text, and there is no `dangerouslySetInnerHTML`.
