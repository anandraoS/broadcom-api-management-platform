# REST API standards

* **Resources and verbs:** plural nouns, nested only one level for ownership (`/apis/{id}/versions`); state transitions as sub-resource `POST` (`/lifecycle`, `/approve`) not `PATCH` of a status field.
* **Status codes:** 200 OK, 201 Created (+ body), 204 No Content, 400 malformed/validation, 401 unauthenticated, 403 forbidden (also object-level), 404 not found, 405, 409 conflict (unique/optimistic lock/invalid transition), 413, 422 semantically invalid (policy/destination rules), 429 with `Retry-After`, 5xx never for client errors.
* **Errors:** RFC 7807 + `code` (machine-readable, stable) + `correlationId` + `errors[]` for field/rule messages. Messages are for humans; clients branch on `code`.
* **Pagination:** `page`/`size` (max 100) + `sort` whitelist; response includes totals; prefer keyset pagination for unbounded/high-churn collections.
* **Filtering:** explicit query parameters, parameterised in the data layer.
* **Idempotency:** `Idempotency-Key` on unsafe create operations that clients may retry; same key + same body = same response; different body = 422.
* **Concurrency:** version field / `ETag` + `If-Match` for updates -> 409/412 when stale.
* **Versioning/compatibility:** see ADR-0010 (additive within a major; removals need a new major + deprecation headers).
* **Security:** authenticate every endpoint (deny by default), authorize by role *and* object, never accept server-controlled fields from clients (DTOs), `Cache-Control: no-store` for secrets, validate and bound every input (size, length, range), no sensitive data in URLs.
* **Documentation:** OpenAPI generated from code (`/v3/api-docs`); examples for error cases; changelog entry for every contract change.
* **Observability:** `X-Correlation-Id` accepted/echoed; one access log line per request with status and duration; no payloads.
* **Limits:** body size, timeouts, rate limits are part of the contract and documented per API.
