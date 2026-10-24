# Multi-agent orchestration (controlled workflow)

> **Status: design + sample artifacts.** No multi-agent run was executed to build this repository; one assistant session worked sequentially in all roles.
> The samples in `tasks/` and `handoffs/` are **illustrative** and say so in their headers. Use this protocol when you *do* run several agents.

## Principles
1. **One owner per file area** (table below); agents propose changes outside their area through a handoff, never edit directly.
2. **Shared contracts are frozen inputs**: `docs/api-contracts.md`, `architecture-rules.md`, ADRs, DB migrations once merged. Only a human-approved ADR changes them.
3. **No self-approval of high-risk work.** Security, migrations, auth, gateway hot path and infra changes need an *independent* reviewer (another agent role or human) plus human approval for sensitive ones.
4. **Evidence, not assertions.** Every handoff lists commands run and their real output summaries; "tests pass" without output is rejected.
5. **Small, dependency-ordered tasks** with explicit inputs/outputs; unknowns become questions, not guesses.

## Roles

| Role | Responsibility | Inputs | Outputs | May change | Must NOT change | Handoff criteria |
|---|---|---|---|---|---|---|
| **R1 Requirements/Spec** | Turn goals into PRD, FR/NFR, acceptance criteria | goal, `project-context.md`, stakeholders' notes | `specs/<n>/prd.md`, `requirements.md` | `specs/**` | code, ADRs | criteria numbered and testable; open questions listed; human approves |
| **R2 Architecture** | Design, API/data contracts, ADR drafts, task graph | approved requirements, `architecture-rules.md` | `design.md`, `tasks.md`, ADR drafts | `specs/**`, `docs/adr/**` (draft status) | implementation code; accepted ADRs | alternatives + failure behaviour covered; invariants respected or ADR proposed; human approves |
| **R3 Java backend** | Implement control-plane/gateway tasks | `tasks.md`, design, contracts | code + unit/IT tests, notes | `control-plane/**`, `gateway/**`, `sample-services/**` (assigned files only) | migrations merged earlier, `infrastructure/**`, `frontend/**`, contracts | `mvn verify` output attached; no invariant violated; diff small |
| **R4 Angular** | Implement UI tasks | tasks, API contract | components, specs, e2e | `frontend/**` | backend, contracts | lint/unit/build outputs attached; no secret/guard-as-security |
| **R5 Test engineering** | Independent test design, negative/failure/security/perf tests | spec, contracts (not the implementation reasoning) | tests + test-plan | `**/src/test/**`, `frontend/e2e/**`, `performance/**` | production code | each acceptance criterion mapped to a test; tests fail on a mutation |
| **R6 Security review** | Threat model delta, review with `api-security-review.md` | diff, design, tests | `security-review.md` (PASS/FAIL per control) | `docs/security.md` proposals, `specs/**/security-review.md` | production code | independent of the author; blocking findings resolved or risk-accepted by a human |
| **R7 Performance/observability review** | Measure, profile, review telemetry/alerts | diff, baseline | `perf-review.md`, dashboard/alert suggestions | `observability/**`, `docs/performance.md` (with real numbers) | production code | numbers with method and caveats; regression flagged |
| **R8 Docs/release** | Docs, runbooks, release notes, readiness | all artifacts | docs, `release.md`, summary | `docs/**`, README | code | evidence table complete; unknowns stated; human go/no-go |

## Task sequencing (dependency-aware)
`R1 -> R2 -> (R3 || R4 on disjoint files, after contracts are frozen) -> R5 (parallel to R3/R4, from the spec) -> R6 + R7 (independent, after implementation) -> R8 -> human release decision.`
Parallelism rule: two agents never share a file; shared types go through the contract docs first. The coordinator (human or orchestrator script) builds a task graph from `tasks.md` and starts only tasks whose dependencies have passed their gate.

## File ownership boundaries
`control-plane/**` and `gateway/**` are R3-owned but only the files named in the task; DB migrations are single-writer (one task at a time); `frontend/**` is R4; `**/src/test/**` is R5 (R3/R4 may add tests for their own code but R5 reviews them); `docs/adr/**`, `architecture-rules.md` are R2 with human approval.

## Conflict resolution
1. Contract conflict (two tasks need different shapes) -> stop both, R2 proposes resolution, human decides, contract updated first.
2. Merge conflict -> the later task rebases and re-runs gates; if semantic, escalate to R2.
3. Disagreement between implementer and reviewer -> reviewer's blocking finding stands until a human overrides in writing.
4. An agent finds the spec wrong -> raise a spec-change handoff to R1/R2; do not silently deviate.

## Review gates (in order)
G1 spec approved (human) -> G2 design approved (human; ADR if invariants change) -> G3 task starts only after dependencies pass -> G4 implementation gate (tests + lint + static analysis outputs) -> G5 independent test review -> G6 security review (independent) -> G7 performance review -> G8 docs complete -> G9 human release approval. **Sensitive changes** (auth, key handling, SSRF, migrations, infra, dependency upgrades on the hot path) require explicit human approval at G6.

## Test evidence requirements
Each handoff includes: commands executed, exit status, counts (tests run/failed/skipped), links to reports, list of skipped checks with reasons, and diff statistics. Unverifiable claims are marked `UNVERIFIED`.

## Final integration checklist
- [ ] All tasks closed with passing gates; no open BLOCKER findings
- [ ] Contracts unchanged or updated with ADR; consumers/producers compatible (N and N-1)
- [ ] `mvn verify`, `mvn -Pstatic-analysis ...`, frontend lint/test/build, smoke test, e2e executed on the merged result (not just per-task)
- [ ] Security and performance reviews attached; exceptions registered with owner/expiry
- [ ] Docs/runbooks/ADRs/OpenAPI updated; release notes drafted
- [ ] Rollback plan and owner named; human go/no-go recorded

## Context hygiene between agents
Hand off *summaries and file paths*, not transcripts. Use `handoffs/_template.md`; the receiving agent loads `project-context.md` + only the modules its role needs (see `../context-engineering-guide.md`).
