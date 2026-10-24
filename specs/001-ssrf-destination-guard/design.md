# 001 - Design *(retrospective)*

```
 register target ─▶ ApiService.addTarget ─▶ DestinationValidator ─✗▶ 422 + audit(TARGET_REJECTED)
                                              │ ok
                                              ▼
                                   DB (backend_target)
 snapshot build ─▶ SnapshotService ─▶ DestinationValidator again ─▶ exclude unsafe (log)
 gateway apply  ─▶ SnapshotPoller.problem ─▶ DestinationGuard.hostAllowed ─✗▶ drop route + INVALID_CONFIG
 each request   ─▶ ProxyService ─▶ DestinationGuard.allowed (hostAllowed + addressesSafe) ─✗▶ 502 destination_not_allowed + event
```
**Alternatives:** blocklist only (bypassable), registration-only check (stored data tampering), egress proxy only (not always available) - see ADR-0008.
**Trade-offs:** three layers cost duplicate code (kept small and identical in rules); per-request resolution adds a DNS lookup (cached by the JVM) and leaves a tiny rebinding window.
**Contract impact:** none (additive error code `unsafe_destination`; snapshot unchanged).
