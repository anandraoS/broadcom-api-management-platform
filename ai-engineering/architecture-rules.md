# Architecture rules (load for any structural change)

Violating one of these needs a new ADR approved by a human, not a code comment.

1. **Control plane is never on the request path.** Gateways must work with only their in-memory/disk snapshot. (ADR-0001)
2. **Fail closed for security, fail static for config.** Missing policy/auth type/unknown values deny; control-plane loss keeps last-known-good. (ADR-0003)
3. **Snapshot application is atomic and validated.** Validate -> build immutable structure -> swap via a single reference. Never mutate live route tables.
4. **Policy changes are versioned, append-only and four-eyed.** No update-in-place of a policy row; revert creates a new version; approver != submitter.
5. **Every destination is allowlisted** at registration, publication and connect time; redirects are not followed. (ADR-0008)
6. **Identity is asserted by the gateway.** Inbound credentials and identity headers are never forwarded; backends see `X-Consumer-Id` set by the gateway.
7. **Authorization is in the service layer** (role + object). Controllers and GraphQL resolvers only delegate. Frontend checks are cosmetic.
8. **Bounded everything:** caches, per-key state, queues, buffers (request/response), page sizes, retries, concurrency per route.
9. **Timeouts and budgets:** one end-to-end budget per proxied request; retries only inside it and only for GET/HEAD/OPTIONS with jitter. (ADR-0006)
10. **Telemetry never blocks or breaks a request.** Async, bounded, drop-oldest; metric labels come only from the registry (`apiSlug/version`) or constants.
11. **Idempotent receivers for at-least-once senders** (telemetry batches, idempotency keys).
12. **Database access** only through repositories; migrations via Flyway, expand/contract, never edited after merge; audit written in the same transaction.
13. **No cross-module imports** between `gateway` and `control-plane`; the contract is JSON documented in `docs/api-contracts.md`.
14. **Config via environment** with safe defaults; no secrets in files; `local` profile is demo-only.
15. **Simplicity:** no new abstraction/library without a stated need; prefer deleting code; no speculative configuration.

**Smells that should trigger a review:** a controller with `if (role...)`, an entity in a DTO, `catch (Exception)` that returns a default, a `static` mutable map, a call to a URL built from request data, a `Thread.sleep` in production code, a new metric label from user input, a migration that drops a column used by the previous release.
