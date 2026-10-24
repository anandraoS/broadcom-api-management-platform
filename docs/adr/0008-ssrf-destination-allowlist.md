# ADR-0008: Backend destination allowlist enforced at three layers

* Status: accepted - Date: 2026-10-17

## Context
Anyone who can register a backend URL can turn a gateway into a proxy to internal systems (cloud metadata `169.254.169.254`, admin ports, other tenants). The registry is an *input surface*.

## Decision
1. **Registration** (`DestinationValidator`): scheme http/https only, no credentials, no query/fragment, host must match an explicit allowlist (`ALLOWED_BACKEND_HOSTS`, exact or `*.suffix`), resolved addresses must not be link-local/any-local/multicast (loopback only when explicitly enabled for demos). Rejections are audited (credentials masked).
2. **Publication** (`SnapshotService`): targets are re-validated when the snapshot is built.
3. **Data plane** (`DestinationGuard`): the gateway checks the host against *its own* allowlist when applying a snapshot and re-resolves addresses before **each** connection; redirects are not followed. Kubernetes `NetworkPolicy` egress restricts what the gateway pod can reach at all.

## Alternatives
Blocklist of bad IPs only (bypassable via DNS, decimal/octal IP forms, redirects); allowlist only at registration (stored data can be tampered with); egress proxy only (good, not always available).

## Consequences
+ Defence in depth: compromising one layer is not enough. - Operational friction: each new backend host needs allowlist changes (a feature); allowlisted names that resolve to private ranges are permitted by design; a tiny DNS-rebinding window remains between check and connect (mitigated by per-request resolution + NetworkPolicy).

## Review triggers
Backends in many namespaces/accounts (move to label-based allowlist or service-discovery integration); evidence of rebinding attempts (pin resolved IPs per connection with a custom `SocketFactory`/egress proxy).
