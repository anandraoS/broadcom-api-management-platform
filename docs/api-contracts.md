# API contracts

OpenAPI is generated from the code at `/v3/api-docs` (Swagger UI: `/swagger-ui.html`); an integration test asserts the
document is published. This file records the conventions and the two internal contracts that OpenAPI does not describe.

## REST conventions (control plane, `/api/v1`)

| Topic | Rule | Where |
|---|---|---|
| Resources | Nouns, plural: `/apis`, `/apis/{id}/versions`, `/apis/{id}/policies`, `/consumers/{id}/keys` | controllers in `web/` |
| Methods / status | `GET` 200; `POST` create 201; state transitions are `POST .../lifecycle`, `.../approve`; `DELETE` 204/200; `PUT` full update with `version` | |
| Validation | Bean Validation on DTO records -> `400 validation_failed` with `errors[]`; semantic rule failures -> `422` (`invalid_policy`, `unsafe_destination`) | `GlobalExceptionHandler` |
| Errors | RFC 7807 `application/problem+json` + `code` + `correlationId` (+ `errors[]`); no stack traces or SQL | `GlobalExceptionHandler` |
| Pagination | `page`, `size` (max 100), whitelisted `sort` field, `dir`; response `{content,page,size,totalElements,totalPages}` | `ApiController.search` |
| Filtering | `q`, `lifecycle`, `owner` (APIs); `type`, `route`, `severity` (events); parameterised JPQL only | repositories |
| Idempotency | `POST /apis` honours `Idempotency-Key` (8-100 URL-safe chars): same key+body replays the stored response (`Idempotent-Replay: true`), same key + different body -> `422` | `IdempotencyService` |
| Concurrency | `PUT /apis/{id}` requires the current `version` (optimistic lock) -> `409 concurrent_modification` when stale | `ApiService.update` |
| Versioning | URI major version (`/api/v1`). Additive changes only within a major (new optional fields/endpoints); removals/renames need `/api/v2` + deprecation window | ADR-0010 |
| Limits | Body <= 2 MiB (`RequestSizeFilter`, Tomcat swallow limit), 10 s connection timeout, page size <= 100 | config |
| Correlation | `X-Correlation-Id` in/out (validated `[A-Za-z0-9._-]{8,64}` else replaced) | `CorrelationFilter` |

### Endpoint summary

| Method & path | Roles | Notes |
|---|---|---|
| `POST /api/v1/auth/token` | public (dev mode only) | dev identity provider; same 401 for unknown user and wrong password |
| `GET /api/v1/apis`, `GET /api/v1/apis/{id}` | any authenticated | |
| `POST /api/v1/apis`, `PUT /api/v1/apis/{id}`, `POST .../lifecycle`, `POST .../versions`, `POST/DELETE .../targets` | ADMIN, API_OWNER of the owning team | object-level check in `AccessGuard` |
| `POST /api/v1/policies/validate` | any authenticated | dry run, no side effects |
| `GET /api/v1/apis/{id}/policies`, `POST` (submit) | read: any; submit: ADMIN / owning API_OWNER | |
| `GET /api/v1/policies/pending`, `POST /policies/{id}/approve|reject`, `POST /apis/{id}/policies/revert` | ADMIN, POLICY_APPROVER | submitter cannot approve own policy |
| `GET/POST /api/v1/consumers`, `.../subscriptions`, `.../keys` | read: any; write: ADMIN, API_OWNER | key plaintext returned once (`Cache-Control: no-store`) |
| `GET /api/v1/audit` | ADMIN, OPERATOR, POLICY_APPROVER | |
| `GET /api/v1/traffic/summary`, `/events`, `/incidents` | any authenticated | |
| `POST /graphql` | any authenticated | same services, same authorization |
| `GET /internal/v1/gateway/snapshot`, `POST /internal/v1/telemetry/batches` | gateway token only | |

## Internal contract 1: gateway snapshot (`GatewaySnapshot` <-> gateway `Snapshot`)

```json
{
  "version": "3f1c...32 hex...",           // SHA-256 prefix of routes+keys (also the ETag)
  "generatedAt": "2026-10-17T10:28:58Z",
  "routes": [{
    "apiSlug": "orders", "versionLabel": "v1", "basePath": "/orders/v1", "policyVersion": 2,
    "targets": [{ "url": "http://orders-service-1:9001", "weight": 2, "healthPath": "/actuator/health" }],
    "policy": { "auth": "API_KEY", "requiredRole": null, "allowedMethods": ["GET","POST"],
                "rateLimitPerMinute": 120, "burst": 20, "rateAlgorithm": "TOKEN_BUCKET", "timeoutMs": 3000,
                "maxRequestBytes": 65536, "ipAllowCidrs": null, "retryEnabled": true }
  }],
  "keys": [{ "prefix": "kdemo", "hash": "<sha256 hex>", "expiresAt": "...", "clientId": "acme-partner", "apiSlugs": ["orders"] }]
}
```

Compatibility rules: the gateway ignores unknown fields (`@JsonIgnoreProperties(ignoreUnknown=true)`) so the control
plane can add fields first; removing/renaming a field requires a two-step rollout (gateways first, then control plane).
Only ACTIVE APIs with an ACTIVE policy and >= 1 valid target are published (fail closed). Keys are published only for
active, non-revoked, non-expired keys of active consumers with an ACTIVE subscription.

## Internal contract 2: telemetry batch

`POST /internal/v1/telemetry/batches` -> `202 {"batchId","storedBuckets"}`. Body: `batchId` (unique per batch), `buckets[]`
(route, bucketStart minute, requests, errors4xx, errors5xx, rateLimited, authFailures, timeouts, latencySumMs, latencyMaxMs;
max 500) and `events[]` (ts, type, severity INFO|WARN|ERROR, route, message<=500; max 500). Re-sending the same batch is safe
(unique key `(batch_id, route_slug, bucket_start)`); the gateway re-sends its in-flight batch until acknowledged.

## Gateway error contract (data plane)

`application/problem+json` body `{status, code, message, correlationId}` with codes: `route_not_found` 404,
`method_not_allowed` 405, `ip_not_allowed` 403, `invalid_path` 400, `unauthorized` 401, `forbidden` 403, `rate_limited` 429,
`payload_too_large` 413, `route_saturated` 503, `no_healthy_backend` 503, `rate_limiter_unavailable` 503,
`gateway_timeout` 504, `bad_gateway` 502 (+ `destination_not_allowed`, `response_too_large`). Messages never contain target URLs,
verifier details or stack traces.

## GraphQL (`/graphql`, schema in `graphql/schema.graphqls`)

Queries: `apis(q, lifecycle, owner, page, size)`, `api(id)` (versions + targets + `events`), `healthSummary(windowMinutes)`,
`recentEvents(route, limit)`. Read-only by design (mutations stay in REST where idempotency/approval semantics live). See
`docs/rest-graphql-protocols.md` for when to choose which.
