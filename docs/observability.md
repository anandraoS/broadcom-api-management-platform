# Observability

## Signals and where they come from

| Signal | Source | Consumer |
|---|---|---|
| Metrics | Micrometer -> `/actuator/prometheus` on every service: `gateway_requests_seconds` (histogram: rate, error ratio, **p50/p95/p99 via `histogram_quantile`**), `gateway_rate_limited_total`, `gateway_auth_failures_total`, `gateway_backend_timeouts_total`, `gateway_retries_total`, `gateway_snapshot_age_seconds`, `gateway_telemetry_dropped_total`, `gateway_ratelimit_redis_failures_total`, JVM, Hikari, process CPU | Prometheus -> Grafana (`observability/grafana/dashboards/gateway-overview.json`) + alerts (`alerts.yml`) |
| Per-route aggregates | Gateway minute buckets -> control plane (`traffic_bucket`, `operational_event`) | Console dashboard + deterministic incident rules |
| Traces | Micrometer Tracing -> OpenTelemetry bridge -> OTLP/HTTP -> Jaeger; W3C `traceparent` injected on the proxied call (`ProxyService.injectTrace`) | Jaeger UI |
| Logs | Structured pattern with `traceId`, `spanId`, `cid` (correlation id) on every line; JSON encoder recommended for production (Logback `LogstashEncoder`) | Any log store (Loki/ELK) |
| Correlation | `X-Correlation-Id` accepted/validated or generated at the edge, echoed in the response, forwarded to backends, stored in audit and events | Support workflow: customer sends the id from an error body |

Metric names above were verified against a running gateway/control plane with `scripts/list-metrics.sh` (counters such as
`gateway_rate_limited_total` appear only after their first increment).

## Cardinality management

* The `route` label is `apiSlug/version` from the **registry** (bounded by registered API versions) or the constant `unmatched`.
  Raw paths, query strings, consumer ids, IPs, user agents and status *codes* (only `status_class`) are never labels.
  Test: `prometheusExposesRouteLevelMetricsWithBoundedLabels` sends 404s to random paths and asserts they do not create label values.
* Budget rule of thumb: series = routes x status classes x metric types; histograms multiply by bucket count (SLO buckets: 50 ms ... 5 s). With 1,000 API versions that is ~1,000 x 5 x 14 = 70k series for the request histogram alone - review before enabling more buckets or labels.
* Per-consumer analytics belong in **logs/traces** (high cardinality tolerated) or a dedicated analytics store, not Prometheus labels.

## Sampling, cost and redaction

* Tracing sampling is configurable (`TRACE_SAMPLING`; compose 0.5, Kubernetes 0.05, tests 0). Head sampling keeps cost predictable but drops rare slow/error traces; the production upgrade is **tail sampling** in an OpenTelemetry Collector (keep 100% of errors and slow traces, ~1-5% of the rest).
* Cost drivers: series count (memory in Prometheus), trace volume (egress + storage), log volume (indexing). Controls: bounded labels, sampling, log level INFO, retention (Prometheus 3 d in compose; telemetry tables 7 d), aggregate before shipping (gateway ships per-minute buckets, not per-request events).
* Redaction: no request/response bodies or headers are logged or traced; correlation ids are the only request-derived log field; URLs with credentials are masked before audit (`safeUrl`).

## Operational workflows

**Elevated latency:** dashboard p95 by route -> compare with backend (`orders-service` panels) to split gateway vs backend time -> open a slow trace in Jaeger using a correlation id/trace id -> check recent config changes (Incidents page "recent changes") -> JVM/GC/Hikari panels -> `docs/troubleshooting/02-latency-after-release.md`.
**High failure rate:** Grafana status-class panel (502 vs 503 vs 504) -> Events page (`CIRCUIT_OPEN`, `TARGET_UNHEALTHY`) -> backend health -> revert last policy/target change if correlated -> `docs/troubleshooting/01-intermittent-502-504.md`.

## Deterministic incident rules (`IncidentRules`)

Thresholds: 5xx rate >= 5% (CRITICAL >= 25%), timeouts >= 2% of requests, average latency >= 1000 ms, 401 share >= 20%, 429 share >= 30%;
minimum 20 requests per route in the window. Every finding includes the measured evidence and fixed investigation steps; event types
`CIRCUIT_OPEN`, `SNAPSHOT_STALE`, `INVALID_CONFIG`, `POOL_EXHAUSTED`, `QUEUE_BACKLOG` map to dedicated findings. **No AI is used in
the incident view.** If an AI assistant were added it would be labelled "hypothesis" and shown separately from this evidence.
Not implemented (listed in the rules/UI as event types the control plane understands): the gateway does not currently emit
`POOL_EXHAUSTED`/`QUEUE_BACKLOG` itself (Hikari saturation is covered by the Prometheus alert `HikariPoolSaturated`).

## If the observability backend is unavailable

OTLP export failures are dropped by the OpenTelemetry SDK exporters (bounded queue) and never block requests; Prometheus scraping
is pull-based so a down Prometheus loses only the scrape gap; telemetry shipping to the control plane buffers then drops oldest.
