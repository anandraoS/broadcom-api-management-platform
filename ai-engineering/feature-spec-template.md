# Feature specification template

Copy to `specs/<nnn>-<name>/spec.md`. Keep it short enough to be read in 10 minutes; every section must be testable. Delete sections only with a stated reason.

```markdown
# <Feature name>
Status: draft | reviewed | approved(human) | implemented | superseded     Owner: <name>     Reviewers: <names>

## 1. Business objective
Why now, for whom, how success is measured (metric + target).

## 2. User stories
As a <role> I want <capability> so that <benefit>.

## 3. Scope / non-goals

## 4. Acceptance criteria (Given/When/Then, numbered, testable)

## 5. API contract
Endpoints/fields/status codes/errors; contract changes classified as additive or breaking (ADR-0010).

## 6. Data model changes
Tables/columns/indexes, migration approach (expand/contract), retention.

## 7. Security implications
Threats (STRIDE rows), authz rules (role + object), secrets, SSRF/abuse, audit events.

## 8. Failure behaviour
For each dependency: what happens when it is slow/down/wrong; degradation mode; user-visible effect.

## 9. Observability requirements
Metrics (names, labels with cardinality estimate), logs, traces, alerts, dashboards, runbook lines.

## 10. Performance expectations
Budget (latency/throughput/memory) and how it will be measured; what is explicitly NOT promised.

## 11. Test scenarios
Unit / integration / e2e / failure / security / performance - one line each, mapped to acceptance criteria.

## 12. Rollout and rollback
Flag/canary, propagation window, monitoring during rollout, exact rollback steps, data compatibility.

## 13. Open questions and decisions (with owners)
```
