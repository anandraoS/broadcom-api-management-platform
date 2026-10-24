# Code-review and architecture-review scenarios

## Code review scenarios (what a good review says)

1. **"Fix 429s by raising the limit to 1,000,000."** Ask: what is the consumer's contract? Is the real cause a retry storm or one noisy client? Prefer per-consumer limits and client backoff guidance; require expected traffic in the PR. *Blocking: no evidence, no rollback plan.*
2. **New endpoint `GET /api/v1/consumers/{id}/audit` checks only `hasRole('API_OWNER')`.** Blocking: BOLA - any owner can read any consumer's audit. Require an ownership check in the service and a negative test (other team -> 403).
3. **Retry added to the proxy for all methods "to be safe".** Blocking: POST/PATCH retries duplicate side effects; require safe-method check, jitter, bounded attempts, a test showing POST is attempted once (`safeMethodsAreRetriedOnTransientFailureButPostIsNot`).
4. **`@Cacheable` on policy lookups with no invalidation.** Blocking: stale policy after approval; propose invalidate-after-commit or no cache (ADR-0004).
5. **`catch (Exception e) { log.error("failed", e); return null; }` in the key lookup.** Blocking: swallows security failures; fail closed with explicit errors.
6. **Large generated PR (3k lines, AI-assisted) touching auth and DB.** Request split by concern; reviewer checks the riskiest 10% (auth, migrations, SQL), reproduces tests locally, asks for provenance and prompt/spec reference.
7. **Migration `ALTER TABLE ... DROP COLUMN` in the same release as the code stopping to use it.** Blocking: expand/contract - drop in the next release.
8. **Nit-level:** naming, duplicated validation, missing `@Transactional(readOnly)`. Approve with nits, don't block.

## Architecture review scenarios

1. **"Let gateways read MySQL directly to avoid the snapshot delay."** Reject: couples data-plane availability to the DB and spreads credentials (ADR-0001/0003); instead shorten the poll interval or add a push hint for revocations.
2. **"Add Kafka for telemetry."** Ask for the requirement (replay? multiple consumers? loss tolerance?). With current needs choose batched idempotent HTTP (ADR-0007); define the trigger that would justify Kafka.
3. **"Support streaming/SSE through the gateway."** The buffered proxy cannot; options: dedicated streaming route type with different limits, or adopt Spring Cloud Gateway/Envoy for those routes (ADR-0002 review trigger). Estimate cost vs demand.
4. **"One policy engine in the UI (client-side evaluation)."** Reject: backend authoritative; UI may only *preview*.
5. **"Multi-region active-active control plane."** Clarify consistency needs for approvals and audit; likely single-writer region + read replicas + gateways with regional snapshot caches; conflict-free revocation design needed before committing.
6. **Review output format:** decision, rationale, risks, alternatives, review trigger -> ADR; reviewers record dissent explicitly.
