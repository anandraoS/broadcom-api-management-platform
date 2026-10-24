# ADR-0005: OIDC/JWT for people, hashed API keys for consumers, shared token for the internal channel

* Status: accepted - Date: 2026-10-17

## Context
Three identity problems: operators (humans), API consumers (applications), gateway->control-plane (service). A local demo must work without an external IdP, but production shape matters.

## Decision
* Operators: Spring Security **resource server** validating bearer JWTs; roles/team from claims. Dev mode: a local HS256 token issuer (disabled by default) producing the same claims as an OIDC provider; production: set `issuer-uri`.
* Consumers: **API keys** `prefix.secret` (256-bit secret, SHA-256 at rest, constant-time compare, expiry <= 365 d, revoke/rotate, subscription scoping) or JWT at the gateway.
* Internal: shared service token with constant-time compare (mTLS/workload identity in production).
* Authorization: RBAC (`@PreAuthorize`) **plus** object-level checks in the service layer (`AccessGuard`); four-eyes approval for policies.

## Alternatives
1. Keycloak in compose: realistic OIDC, heavy and slower to start; documented as the production-like option, not required for the demo.
2. Opaque tokens + introspection: revocable but adds a network call per request.
3. Slow password hashing (bcrypt) for API keys: unnecessary for 256-bit random secrets and would add ~100 ms per request.
4. Sessions + CSRF: unnecessary for a token-based SPA/API; more attack surface.

## Consequences
+ No code differences between demo and production resource-server behaviour; keys are useless if the DB leaks; cheap per-request auth.
- Dev HS256 secret is symmetric (anyone with it can mint tokens) - strictly local; static shared token must be rotated manually until mTLS; JWT revocation relies on short TTLs.

## Review triggers
Partner onboarding needs OAuth client-credentials (move consumer auth to the IdP); compliance requires mTLS between components; key volume grows (add pepper/HMAC and key-usage analytics).
