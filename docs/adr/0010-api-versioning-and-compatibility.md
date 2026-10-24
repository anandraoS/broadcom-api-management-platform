# ADR-0010: API versioning and backward-compatibility rules

* Status: accepted - Date: 2026-10-17

## Decision
* **Control-plane REST:** major version in the URI (`/api/v1`); inside a major version only *additive* changes (new optional request fields with defaults, new response fields, new endpoints). Removing/renaming fields, tightening validation of existing fields, changing status codes or semantics requires `/api/v2` and a documented deprecation period (minimum two releases / 90 days, `Deprecation` and `Sunset` headers).
* **Managed APIs (what the platform governs):** each API has versions with their own base path (`/orders/v1`, `/orders/v2`), lifecycle (`DRAFT -> ACTIVE -> DEPRECATED -> INACTIVE`) and policy history. Deprecated versions keep routing but are visible as deprecated in the registry; consumers are notified via subscription contacts before deactivation.
* **Internal snapshot contract:** consumers (gateways) ignore unknown fields; producers add fields first, then consumers start using them; removal is a two-step rollout.
* **Database:** expand/contract migrations so N and N+1 application versions run against the same schema during rolling deploys.
* **GraphQL:** add fields freely, deprecate with `@deprecated`, never reuse a field name with a different meaning.

## Alternatives
Header-based versioning (hidden from logs/caches, harder to route); date-based versions (good for pure SaaS APIs, harder for gateway routing); no versioning with feature flags (consumer-hostile).

## Consequences
+ Predictable, routable, testable compatibility; gateway routing per version. - Operating multiple versions costs support and policy duplication; discipline needed to avoid "breaking additive" changes (e.g. a new *required* field).

## Review triggers
More than two live majors simultaneously; consumers requesting finer-grained evolution (consider contract tests with consumer-driven contracts, e.g. Pact).
