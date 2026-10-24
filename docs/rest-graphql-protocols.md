# REST, GraphQL, HTTP/TLS and integration patterns

## REST vs GraphQL (both implemented for the control plane)

| Aspect | REST (`/api/v1`) | GraphQL (`/graphql`, read-only) |
|---|---|---|
| Fit | Commands and state transitions (approve, revert, issue key) with clear idempotency/authorization semantics; cacheable reads | The console's "API + versions + targets + recent events" view in **one** round trip |
| Over/under-fetching | Fixed shapes; several calls for composite screens | Client selects fields |
| Performance | Simple, CDN/HTTP-cache friendly (`GET` + ETag); N calls | One call but **query cost is client-controlled**: depth/complexity/pagination limits are required (schema is small, page size capped at 100, no mutations, no recursive types) |
| N+1 risk | Server-side joins per endpoint | Resolver-level N+1 (e.g. `Api.events` per API) - bounded by page size here; use DataLoader/batch mappings when lists grow |
| Authorization | Method + object checks per endpoint | Must be enforced **per field/resolver or in the service layer**, never only at the HTTP edge; here both transports call the same services, so object-level checks apply to both |
| Errors | HTTP status + problem+json | Always HTTP 200 with an `errors[]` body - monitoring must inspect the body |
| Versioning | URI major version, additive changes | Schema evolution by adding fields, deprecating with `@deprecated`; avoid breaking removals |
| Idempotency/approval flows | `Idempotency-Key`, `If-Match`-style version fields | Awkward for mutations; deliberately not offered |

**Rule used here:** reads that aggregate across resources -> GraphQL; anything that changes state or needs audit/approval/idempotency
semantics -> REST. Partners calling the *gateway* use plain HTTP/REST of whatever the backend exposes.

## HTTP, HTTPS, TLS, HTTP/2, HTTP/3

| Topic | Status in this project |
|---|---|
| HTTP/1.1 | **Implemented** end to end (consumer -> gateway via Tomcat; gateway -> backend via JDK `HttpClient`, forced to 1.1 to avoid h2c upgrade surprises). Keep-alive pooling is handled by the client. |
| HTTPS / TLS | **Assumed at the edge**: ingress terminates TLS (cert-manager example in `infrastructure/k8s/40-ingress-networkpolicy.yaml`); HSTS is emitted when the control plane sees HTTPS. The local demo is plain HTTP on localhost. Production: TLS 1.2+/1.3, strong ciphers, certificate rotation, mTLS between gateway <-> backends/control plane. |
| HTTP/2 | **Conceptual for the gateway.** Tomcat supports it (`server.http2.enabled=true` + TLS) and the JDK client can speak it; not enabled because the benefit (multiplexing, header compression) matters most between browsers and the edge, which the ingress controller provides. Trade-off: HTTP/2 multiplexes many requests on one TCP connection (less connection overhead, but TCP head-of-line blocking under loss, and a single bad connection affects many streams). |
| HTTP/3 (QUIC) | **Conceptual only.** UDP-based, no TCP head-of-line blocking, faster handshakes, connection migration. Terminate it at a CDN/edge proxy that supports it (and keep HTTP/1.1 or h2 inside the cluster); Tomcat/JDK client in this stack do not provide it. |
| Redirects | Never followed by the gateway (a redirect from a backend could bypass the destination allowlist) |
| Hop-by-hop headers | Stripped (`Connection`, `Transfer-Encoding`, `Upgrade`, ...); `Host`/`Content-Length` recomputed |
| Forwarded headers | `X-Forwarded-For/Proto/Host` set by the gateway; inbound values are discarded; the gateway trusts XFF only when `TRUST_FORWARDED_FOR=true` behind a trusted ingress |

## Secure integration patterns for external services

1. **Allowlist, don't blocklist** destinations; resolve and verify addresses; no redirects; no URL-embedded credentials.
2. **Timeouts everywhere** (connect, read, end-to-end), bounded response size, retries only for idempotent calls, circuit breaker.
3. **Credentials**: short-lived tokens or mTLS; never forward the caller's credentials to a backend; assert identity with gateway-signed/controlled headers.
4. **Webhooks/callbacks** (not implemented): sign payloads (HMAC with timestamp + replay window), validate destinations with the same SSRF controls, retry with backoff + DLQ.
5. **Observability**: propagate correlation ids / `traceparent`; never log secrets or payloads.

## FTP vs SFTP (conceptual - no file-transfer infrastructure is added)

| | FTP | FTPS | SFTP |
|---|---|---|---|
| Protocol | Own protocol, control + dynamic data channels | FTP over TLS | SSH subsystem (single port 22) |
| Confidentiality | **None** (credentials and data in clear text) | TLS-protected | SSH-protected |
| Firewalls | Awkward (active/passive data ports) | Awkward | Easy (one port) |
| Auth | Password | Password / client cert | Password / SSH keys (preferred) |
| Verdict | Do not use across untrusted networks | Acceptable when partners mandate it | Default for B2B batch file exchange |

A meaningful scenario for this platform would be *partner bulk import* (e.g. consumer onboarding CSV): accept it over SFTP into an isolated
landing zone, scan/validate, then process asynchronously with idempotent row handling and an error file. It is **not implemented**
because the platform's core flows are API-based and an SFTP server would add attack surface without a requirement.
