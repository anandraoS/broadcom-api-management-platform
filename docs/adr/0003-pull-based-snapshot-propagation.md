# ADR-0003: Pull-based, versioned snapshot propagation with last-known-good

* Status: accepted - Date: 2026-10-17

## Context
Gateways need configuration changes quickly but must survive control-plane outages and never apply a broken config.

## Decision
Gateways **poll** `GET /internal/v1/gateway/snapshot` every few seconds with `If-None-Match` (content-hash ETag). They validate the whole document, build an immutable route table, swap it atomically, and persist it as last-known-good. On any error they keep serving the previous snapshot.

## Alternatives
1. **Push (webhook/SSE/WebSocket) from the control plane:** lower latency, but needs gateway registry, retry/ordering logic and handling of missed pushes - polling is still needed as a safety net.
2. **Message broker (Kafka/RabbitMQ) fan-out:** durable and ordered; adds a broker to operate for a payload that is small and idempotent (full state, not deltas). Reconsider at large fleet sizes.
3. **Shared DB/Redis read on every request:** simplest consistency, worst latency and failure coupling.
4. **Delta updates:** efficient for huge configs, harder to reason about; full snapshots are self-healing.

## Consequences
+ Simple, idempotent, self-healing, trivially cacheable (304); outage-tolerant; no per-gateway state in the control plane.
- Propagation is bounded but not instantaneous (poll interval + <= 30 s control-plane cache staleness on other replicas); the control plane sees poll load proportional to gateway count (mitigated by `304`); revocations are delayed by the same window.

## Review triggers
Required revocation latency below the poll interval; fleet > ~100 gateways; snapshot > ~5 MB (move to deltas or sharded snapshots).
