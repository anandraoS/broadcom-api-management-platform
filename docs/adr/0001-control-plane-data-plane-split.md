# ADR-0001: Separate control plane and data plane

* Status: accepted - Date: 2026-10-17 - Deciders: platform architecture

## Context
Policy management (low QPS, needs transactions, audit, approvals) and request proxying (very high QPS, latency-critical) have opposite
scaling and availability profiles. A shared process would couple operator outages to customer traffic.

## Decision
Two deployables. The **control plane** owns state in MySQL and publishes an immutable snapshot; the **gateway** is stateless, pulls the snapshot
and serves traffic from memory. Gateways never query the database.

## Alternatives considered
1. *Single application* serving both: simplest, but a slow admin query or deploy can hurt traffic; rejected.
2. *Gateway reads the database directly*: removes a hop but couples gateway scaling to DB capacity and spreads DB credentials into the data plane; rejected.
3. *Push via message broker* (see ADR-0003): lower propagation delay, more infrastructure.

## Consequences
+ Traffic survives control-plane outages (fail static); independent scaling and release cadence; smaller blast radius.
- Eventual consistency of configuration (seconds); a versioned contract between the two must be maintained (`docs/api-contracts.md`); two deployables to operate.

## Review triggers
Propagation delay becomes a business problem (e.g. need < 1 s revoke); more than ~50 gateways (poll load); regulatory need for synchronous revocation.
