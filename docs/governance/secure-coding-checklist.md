# Secure coding checklist (use in every PR touching code or config)

**Authentication and access control**
- [ ] New endpoint is authenticated by default and has an explicit role rule
- [ ] Object-level check for every `{id}` that belongs to a team/tenant (BOLA)
- [ ] No client-supplied identity/role/owner field is trusted
- [ ] Tokens: signature + issuer + expiry + (audience) verified; failures return the same generic response

**Input and output**
- [ ] Bean Validation for syntax; semantic validators for rules; size/length/range limits
- [ ] Only parameterised queries; sort/filter fields whitelisted
- [ ] No string concatenation into SQL, shell, URLs, logs (log injection) or HTML
- [ ] Outbound URLs come from the allowlisted registry, never from request data (SSRF); redirects not followed
- [ ] Error responses contain no stack traces, SQL, hostnames or internal addresses

**Secrets and data**
- [ ] No secrets, tokens, keys, customer data in code, tests, fixtures, logs, metrics labels, traces, AI context files
- [ ] Secrets only via env/secret manager references; dummy values clearly labelled local-only
- [ ] Credentials hashed (high-entropy tokens: SHA-256; passwords: bcrypt/argon2); shown once; rotation/expiry supported
- [ ] PII not stored/logged unless required, with retention noted

**Resilience and abuse**
- [ ] Timeouts on every outbound call; retries only for idempotent operations with backoff + jitter
- [ ] Caches/queues/maps are bounded; no unbounded per-user/per-IP state
- [ ] Rate limits and size limits considered for new endpoints

**Dependencies and build**
- [ ] New dependency justified, BOM-managed or pinned with reason; scan results reviewed
- [ ] Container runs non-root, read-only FS, no extra capabilities
- [ ] CORS origins explicit; CSRF decision documented for the auth mechanism used

**Audit and operations**
- [ ] Privileged/administrative actions audited (actor, action, entity, correlation id) in the same transaction
- [ ] New failure mode has a metric/alert/runbook line
- [ ] Security tests added (negative cases: wrong role, other team, malformed, oversized, expired)
