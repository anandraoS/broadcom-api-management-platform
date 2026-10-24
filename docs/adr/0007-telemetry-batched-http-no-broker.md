# ADR-0007: Telemetry as batched idempotent HTTP; no Kafka/RabbitMQ

* Status: accepted - Date: 2026-10-17

## Context
Gateways must report per-route aggregates and events for the incident view without risking request latency. The brief allows Kafka/RabbitMQ "where justified".

## Decision
Gateways aggregate per route per minute in memory, then POST batches (<= 500 buckets / 500 events) to the control plane every 15 s with a unique `batchId`; the in-flight batch is re-sent until `202`. The receiver de-duplicates on `(batchId, route, minute)`. Buffers are bounded (drop oldest, counted). Precise percentiles/time series live in Prometheus (pull), not here.

## Alternatives
1. **Kafka:** durable, replayable, great for per-request analytics at large scale; needs brokers, topic/partition management, consumer lag monitoring, schema governance - disproportionate for ~N routes x 1 bucket/min.
2. **RabbitMQ:** simpler ops, acks/DLQ built in; still a stateful dependency for a loss-tolerant, small payload.
3. **Direct per-request POST:** couples latency and availability; rejected.
4. **Prometheus remote-write only:** good for metrics, but the console also needs events and a deterministic rules engine over structured data.

## Consequences
+ Zero extra infrastructure; at-least-once + idempotent receiver = effectively-once storage; bounded memory; control-plane outage cannot hurt traffic.
- Data is lost if a gateway dies before flushing or the control plane is down longer than the buffer; no replay; dead-letter handling is "reject 4xx -> count and drop".

## Review triggers
Need per-request analytics, replay, multiple independent consumers (billing, security analytics), or data-loss intolerance -> introduce Kafka with a transactional outbox/idempotent consumer and a DLQ topic.
