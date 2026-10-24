# 001 - Security review *(retrospective; reviewer role played by the same assistant - a real review needs an independent human)*

| Control | Result | Evidence |
|---|---|---|
| Allowlist deny-by-default | PASS (code) / CAUTION (config) | an empty list rejects everything (`DestinationProperties`); the shipped `application.yml` default lists only the demo hostnames, so production **must** set `ALLOWED_BACKEND_HOSTS` explicitly |
| Blocklist of metadata/any-local/multicast | PASS | validator test |
| Credentials/query rejected, masked in audit | PASS | IT asserts audit has no password |
| Redirects not followed | PASS (by configuration) | `HttpClient.Redirect.NEVER` in `ProxyService` (no dedicated test: **gap**) |
| Re-check at connect time | PASS with residual risk | `ProxyService` calls `DestinationGuard.allowed`; DNS-rebinding window remains |
| Network layer | NOT VERIFIED | NetworkPolicy manifests never applied |
Open risks: allowlisted hostnames resolving to private ranges are permitted by design; IPv6/odd literal encodings rely on `InetAddress` classification; add a redirect test and an egress proxy for strict environments.
