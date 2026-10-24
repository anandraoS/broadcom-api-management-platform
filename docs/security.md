# Security

## 1. Authentication and authorization design

| Actor | Mechanism | Implementation | Production target |
|---|---|---|---|
| Operator / API owner (UI, REST, GraphQL) | OAuth 2.0 bearer JWT, validated by Spring Security resource server (signature, `exp`, issuer); roles from the `roles` claim, team from `team` | `SecurityConfig`, `JwtAuthenticationConverter` | Set `spring.security.oauth2.resourceserver.jwt.issuer-uri` (OIDC discovery + JWKS rotation), remove `app.security.jwt-secret` and `DEV_AUTH_*`. No other code changes. |
| Dev login (local only) | `POST /api/v1/auth/token` issues short-lived HS256 tokens with the same claims an OIDC provider would; disabled unless `app.dev-auth.enabled=true` | `AuthController` (conditional bean) | Disabled/removed |
| Gateway -> control plane | Shared token header, constant-time compare, `ROLE_GATEWAY` only on `/internal/**` | `GatewayTokenFilter` | mTLS (SPIFFE/service mesh) or workload-identity-issued JWT |
| API consumer at the gateway | **API key** `prefix.secret` (256-bit random secret; only SHA-256 stored; shown once; expiry <= 365 d; revoke = rotate) or **JWT** | `ConsumerService`, `Authenticator` | Same; JWT verified against the IdP JWKS |
| Service-to-service (gateway -> backend) | Gateway-asserted `X-Consumer-Id`; inbound credentials stripped; backends must only be reachable from the gateway (NetworkPolicy) | `ProxyService` | mTLS to backends |

**Object-level authorization.** `API_OWNER` is scoped to a team (`team` claim). `AccessGuard.assertCanModify` runs in the
service layer, so REST and GraphQL share it. Tests: `ownerCannotModifyAnotherTeamsApi_BOLA`, `ownerCannotCreateApiForAnotherTeam`.
**Four eyes.** A policy cannot be approved by its submitter (`self_approval`, 403).
**Frontend.** Route guards and hidden menu items are UX only; every endpoint authorizes independently. The Angular app never
holds a backend secret; the only credential it sees is the user's short-lived access token.

## 2. Threat model (STRIDE-style, by asset)

| # | Threat | Attack | Controls (code) | Residual risk / next step |
|---|---|---|---|---|
| T1 | Unauthorized API access | Calls without/with forged/expired token or key | JWT signature+issuer+exp checks; key hash, expiry, subscription check; same 401 body for all failures; tests `expiredTokenIsRejected`, `tokenFromWrongIssuerOrSignatureIsRejected`, `apiKeyPolicyRejectsMissingWrongExpiredAndUnsubscribedKeys` | Dev HS256 secret is shared; use OIDC in prod |
| T2 | Broken object-level authorization (OWASP API1) | Owner of team A edits team B's API/policy by id | `AccessGuard` in service layer; 403 `not_owner`; consumer sub-resources are scoped by `consumerId` in queries (`filter(x -> x.getConsumer().getId().equals(...))`) | Consumers have no per-owner scoping yet (ADMIN/API_OWNER of any team can manage any consumer) - tracked as a gap |
| T3 | Credential leakage | Keys in logs/UI/audit, secrets in git/images | Plaintext key returned once, `Cache-Control: no-store`, list view has prefix only; audit never stores keys; rejected-URL credentials masked before audit; `.env` ignored; no secrets in images/ConfigMaps; secrets by reference | Dev passwords exist as dummy defaults in `local` profile only |
| T4 | Malicious route/target configuration | Owner registers `http://169.254.169.254` or internal admin host; path tricks | `DestinationValidator` (scheme, no userinfo, host allowlist, blocked IP ranges, no query/fragment) at registration **and** snapshot build; gateway `DestinationGuard` re-checks allowlist on apply and resolved addresses on each request; path-safety check; tests `DestinationValidatorTest`, `unsafeBackendDestinationsAreRejectedAndAudited`, `routeWithDisallowedBackendHostIsRejectedAndNeverServed` | DNS rebinding window between check and connect (mitigated by per-request resolve, not eliminated: use egress NetworkPolicy/proxy) |
| T5 | SSRF via gateway | Using the proxy to reach metadata/internal services | Allowlist + blocklist above; redirects **not** followed; NetworkPolicy egress restricts gateway to approved pods; backend URLs never taken from requests | Allowlist hosts that resolve to private IPs are allowed by design (`allow-loopback=false` only blocks loopback) |
| T6 | Denial of service / resource exhaustion | Floods, slow clients, huge bodies, retry storms | Per-consumer/route limits, bulkhead, bounded body read, response cap (10 MiB), Tomcat timeouts, retry budget inside end-to-end timeout, circuit breaker, ingress `limit-rps`, HPA | Volumetric L3/L4 DDoS needs a CDN/WAF in front |
| T7 | Sensitive information exposure | Verbose errors, PII in telemetry | RFC 7807 bodies without internals; telemetry labels limited to `apiSlug/version`; no payloads, headers, IPs or consumer ids in metrics; audit `detail` is text rendered as text | Logs may contain correlation ids and API slugs only |
| T8 | Compromised dependency / image | Malicious or vulnerable library | Pinned BOM; CI Trivy fs+image scan, SBOM (CycloneDX), hadolint, minimal non-root JRE image, read-only root FS; real `npm audit` finding documented in `docs/governance/vulnerability-remediation.md` | Maven scanning only runs in CI (not executed in this build env) |
| T9 | Misconfigured Kubernetes | Privileged pods, open network, secrets in env | PSA `restricted`, `runAsNonRoot`, drop ALL caps, read-only FS, no SA token, default-deny NetworkPolicies, PDBs, resource limits | Env-var secrets are visible to anyone who can `exec`; prefer mounted files/CSI in strict environments |
| T10 | Replay / tampering of config | Forged snapshot to gateways | Authenticated channel, validation on apply, last-known-good retained; TLS/mTLS in prod | Snapshot is not signed (add detached signature if the channel is untrusted) |
| T11 | Audit tampering | Edit/delete history | Append-only entity (no setters, no update/delete API), written in the same transaction as the change | DB admin can still alter rows: ship to WORM storage / SIEM |
| T12 | Brute-force of API keys | Guess `prefix.secret` | 256-bit secrets, constant-time compare, hash computed even for unknown prefixes, auth failures counted/alerted, rate limit per IP for anonymous | Per-IP limit on *failed* auth is not separate from the route limit |

## 3. OWASP mapping

**Top 10 (2021):** A01 access control (RBAC + BOLA guard + deny by default `anyRequest().authenticated()`); A02 crypto (key hashes, TLS assumptions, no custom crypto); A03 injection (parameterised JPQL, Bean Validation, header/path validation; `searchTermIsParameterisedAgainstSqlInjection`); A04 insecure design (threat model, fail-closed config, four eyes); A05 misconfiguration (secure defaults, PSA, no secrets in config, CORS allowlist); A06 vulnerable components (scanning, SBOM); A07 authn failures (generic errors, short token TTL, key expiry); A08 integrity (validated snapshots, signed images in CI roadmap); A09 logging (structured logs, correlation ids, audit); A10 SSRF (section T4/T5).
**API Security Top 10 (2023):** API1 BOLA (above); API2 broken authN (above); API3 property-level (DTOs, no entity exposure; mass assignment impossible); API4 unrestricted resource consumption (limits/bulkhead/size caps); API5 function-level authz (`@PreAuthorize` + service checks); API6 sensitive business flows (idempotency, four eyes); API7 SSRF (above); API8 misconfiguration (headers, CORS, error handling); API9 inventory (registry with lifecycle/deprecation); API10 unsafe consumption of APIs (timeouts, size caps, redirects off, no trust in backend headers).

## 4. Web security specifics

* **CSRF:** disabled intentionally - bearer tokens in the `Authorization` header, no cookie auth. If cookies are ever added (e.g. BFF pattern), enable CSRF and `SameSite=Strict`.
* **CORS:** explicit origin allowlist (`app.security.cors-origins`), explicit headers/methods, `maxAge` 600 s; the shipped UI uses same-origin proxying (no CORS needed).
* **Headers:** `X-Content-Type-Options`, `X-Frame-Options: DENY`, HSTS (HTTPS only), CSP on the nginx image (`default-src 'self'`).
* **TLS:** terminated at the ingress (cert-manager example); internal hops are plaintext only in the local demo. Production: TLS 1.2+ (1.3 preferred), mTLS between components, HSTS preload only after validation.
* **Output handling:** Angular templates escape by default; audit/event text is bound with interpolation, never `innerHTML`.
* **Token storage (UI):** `sessionStorage` for the demo (cleared on tab close; XSS can read it). Production: authorization-code + PKCE, in-memory token, refresh via a BFF or silent renew.
* **Secrets management:** none in git; compose requires `.env` (`:?` fails fast); Kubernetes uses `existingSecret` references (External Secrets/CSI); rotate by issuing a new key then revoking the old; gateway token rotation = deploy both sides with overlapping value list (future work: support two tokens).
* **Redaction:** correlation ids only in logs; the `safeUrl` helper masks `//user:pass@`; no request/response bodies are logged anywhere.

## 5. Demonstration vs production security

| Area | Demo (this repo, local) | Production requirement |
|---|---|---|
| Identity | HS256 dev tokens, dummy password | OIDC provider, MFA, JWKS rotation |
| Component auth | Shared gateway token | mTLS / workload identity |
| Transport | HTTP on localhost | TLS 1.2+/mTLS everywhere |
| Secrets | `.env` / dummy defaults | Secret manager, rotation, no env-var exposure where avoidable |
| Data stores | H2 in memory / compose MySQL | Managed MySQL with TLS, backups, least-privilege DB user (DML only; Flyway runs with a separate DDL user) |
| Images | Built locally | Signed (cosign), digest-pinned, scanned, SBOM attached |
| Network | Docker bridge | Default-deny NetworkPolicies, egress control, WAF/CDN |

## 6. Security regression tests

`DestinationValidatorTest` (10), `PolicyValidatorTest` (10), `ControlPlaneApiIT` (authn/z, BOLA, SQLi, SSRF, idempotency, size limit,
correlation-id injection), `GatewayRoutingIT` (auth matrix, spoofed identity headers, traversal, SSRF snapshot, size, rate limit),
`PathSafetyTest`, Angular `interceptors.spec` (token never sent to third-party URLs) and Playwright (viewer cannot reach approvals).
