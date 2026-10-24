# Code review checklist

Reviewer mindset: *does it do the right thing, safely, observably, and can we undo it?* Generated (AI) code is reviewed exactly like human code - and treated as untrusted until it passes this list and the tests.

1. **Intent:** matches the spec/acceptance criteria? Edge cases and failure paths handled? Is anything missing that the ticket implies (audit, metrics, docs)?
2. **Correctness and business logic:** state transitions legal? Idempotent where retried? Off-by-one in limits/windows? Time handled via injectable clock and UTC?
3. **Architecture boundaries:** controller -> service -> repo respected? No entity leaks, no cross-module imports (gateway must not import control-plane code)? No new shared mutable state?
4. **Security:** run `secure-coding-checklist.md`; any new endpoint/role/URL source/secret/log line gets explicit scrutiny.
5. **Authorization:** role *and* object checks? What happens for another team's id? For a disabled consumer?
6. **Data:** migration safe (expand/contract, backwards compatible)? Index for every new filter/sort? Transaction boundaries and locking sane? N+1?
7. **Concurrency/resources:** race conditions, bounded collections, thread-safety, resources closed, virtual-thread pinning, pool sizing.
8. **Resilience:** timeouts, retries (safe only), breaker/bulkhead impact, behaviour when dependency is down, graceful degradation.
9. **Performance:** hot-path allocations, blocking calls, per-request DNS/regex/JSON work, payload buffering, metrics cardinality.
10. **Observability:** logs with correlation id (no PII), metrics with bounded labels, alert/runbook updates for new failure modes.
11. **Tests:** fail without the change? Negative paths? No sleeps? Regression test for bug fixes? Skipped tests explained? Coverage of the risky branch, not just lines.
12. **Simplicity:** no speculative abstraction/configuration, no dead code, names say intent, comments explain why.
13. **Docs/ops:** README/ADR/runbook/OpenAPI updated; rollout and rollback described in the PR; feature flags/defaults safe.
14. **AI-generated content:** provenance noted in the PR; every claim verified by running code; no invented APIs/versions; no secrets in prompts or output.

Outcome vocabulary: *Approve*, *Approve with nits*, *Request changes (blocking: security, correctness, data loss, missing tests for risk)*. Security/data/gateway/infra changes need a CODEOWNER from the owning area.
