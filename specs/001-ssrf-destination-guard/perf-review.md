# 001 - Performance review *(retrospective)*

* Registration/snapshot validation: off the hot path (milliseconds, rare).
* Hot path cost: one `InetAddress.getAllByName` per proxied request. Not isolated by a dedicated benchmark; the gateway end-to-end numbers (`docs/performance.md`) include it, with the JVM's positive DNS cache (default 30 s) absorbing repeated lookups of the same host.
* Risk recorded in `docs/troubleshooting/02-latency-after-release.md` (slow/uncached resolvers). **Recommended follow-up:** inject a resolver, cache safe-resolution results with a short TTL, and add a micro-benchmark. Status: not done.
