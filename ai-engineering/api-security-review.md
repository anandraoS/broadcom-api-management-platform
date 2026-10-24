# API security review module

Walk the checklist for any change touching endpoints, policies, destinations, auth, secrets, logging or Kubernetes config. Cite code and test evidence for each "pass".

1. **Authentication:** every route authenticated unless explicitly public; tokens validated for signature, issuer, expiry (and audience where applicable); generic failure messages; dev-only auth cannot be enabled by default.
2. **Authorization:** role check present *and* object-level check for ids (other team, other consumer, disabled entity); GraphQL shares service checks; internal endpoints accept only the gateway credential.
3. **Input:** size/length/range limits; Bean Validation + semantic validators; allowlisted enums; safe sort fields; path/header validation (dot-segments, CRLF, null bytes).
4. **Injection:** no string-built SQL/JPQL/shell/URLs/log lines; correlation ids validated before logging.
5. **SSRF:** destination allowlist + blocked ranges + no credentials/redirects; re-check at connect; Kubernetes egress policy.
6. **Secrets:** none in repo/images/logs/metrics/traces/AI context; shown once; hashed at rest; rotation/expiry; constant-time comparison.
7. **Resource abuse:** rate limits, bulkheads, body/response caps, timeouts, retry budgets, pagination caps.
8. **Data exposure:** DTOs only, no stack traces, no internal hostnames/URLs in errors, `Cache-Control: no-store` for secrets, PII minimised.
9. **Transport/headers:** TLS at the edge, HSTS, nosniff, frame deny, CSP for the UI, CORS explicit origins.
10. **Supply chain:** dependency changes justified; scanners reviewed; images non-root, read-only FS.
11. **Audit:** privileged actions recorded atomically with actor and correlation id.
12. **Tests:** at least one negative test per new control (wrong role, other team, malformed, oversized, expired, spoofed header).

**Report format:** `PASS/FAIL/NA - control - evidence (file:line or test name)`; unresolved risks go to the threat-model table in `docs/security.md` with an owner.
