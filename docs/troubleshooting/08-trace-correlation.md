# 08 - Incorrect distributed trace correlation

**Symptoms:** Jaeger shows the gateway span and the backend span as *separate traces*; logs carry different `traceId`s for one user request; the correlation id search finds the gateway log but not the backend log.

**Hypotheses:** (1) `traceparent` not propagated on the proxied call; (2) backend does not extract it (no tracing instrumentation or different propagator, e.g. B3); (3) gateway strips/overwrites it; (4) sampling decisions differ (parent sampled, child not); (5) async hand-off loses MDC/context; (6) proxy/ingress in between drops the header; (7) clock skew makes spans look disjoint.

**Evidence:** inspect headers the backend actually receives (`orders-service` `/echo` returns them) - expect `traceparent` and `x-correlation-id`; compare `traceId` in gateway vs backend logs; Collector/Jaeger sampling settings; propagator config (`management.tracing.propagation.type`).

**Commands**
```bash
curl -s -H "X-API-Key: $KEY" -H 'X-Correlation-Id: demo-corr-0001' localhost:8081/gw/orders/v1/echo | grep -io 'traceparent[^,]*'
kubectl -n api-platform logs deploy/orders-service | grep demo-corr-0001
```

**Root cause (illustrative - a proxy built this way would fail like this):** the proxy copies inbound headers but drops `traceparent` as if it were a hop-by-hop header and never re-injects the *gateway's* span context, so each backend call starts a new root trace. The implementation in this repository strips inbound trace headers (to avoid duplicates) **and** injects the current span context (`ProxyService.injectTrace` via Micrometer `Propagator`) - keep that pairing when refactoring.
**Regression tests:** `routesTrafficRewritesPathAndPropagatesCorrelationId` checks the correlation id; add an assertion that the backend receives exactly one `traceparent` whose trace id equals the gateway's current trace (needs sampling probability 1.0 in that test).
**Prevention:** contract test for propagation headers, W3C `traceparent` everywhere (avoid mixing B3), log pattern with `traceId`/`spanId`, tail-sampling at the Collector (keeps errors/slow traces), correlation id as the human-friendly fallback.
