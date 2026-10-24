# 001 - Requirements *(retrospective)*

**Functional**
- FR1 Only `http`/`https` schemes. FR2 No credentials/userinfo in URLs. FR3 No query/fragment. FR4 Host must match the configured allowlist (exact or `*.suffix`).
- FR5 Resolved addresses must not be link-local (incl. `169.254.169.254`), any-local, multicast; loopback only when explicitly enabled.
- FR6 Violations return `422 unsafe_destination` with reasons and write a `TARGET_REJECTED` audit event (credentials masked).
- FR7 The control plane re-validates targets when building the snapshot; unsafe targets are excluded.
- FR8 The gateway validates against **its own** allowlist when applying a snapshot and re-resolves addresses before each connection; redirects are not followed.

**Non-functional**
- NFR1 Validation is pure and unit-testable; NFR2 per-request resolution must be cheap (JVM DNS cache; measured risk in `docs/troubleshooting/02-latency-after-release.md`); NFR3 no information leakage in data-plane errors; NFR4 allowlist configured via environment; an empty list denies everything (the shipped YAML default contains only the demo hostnames - production must override it).
