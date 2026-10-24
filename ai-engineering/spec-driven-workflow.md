# Specification-driven development workflow

A plan -> spec -> implement -> review loop that works with or without any specific tool or methodology name (BMAD-style role phases, spec-kit style
`specify/plan/tasks`, or plain pull requests). Methodologies evolve; **the artifacts below are the contract**.

| # | Phase | Output artifact (in `specs/<nnn>-<name>/`) | Gate to leave the phase |
|---|---|---|---|
| 1 | Product requirements | `prd.md`: problem, personas, objective + metric, non-goals | Product owner approves scope |
| 2 | Functional + non-functional requirements | `requirements.md`: numbered FR/NFR (performance, security, availability, compliance) | Each requirement testable; reviewers sign |
| 3 | Architecture and design | `design.md`: components, data model, API contract, failure behaviour, alternatives; ADR if an invariant changes | Architect review; ADR accepted |
| 4 | Task decomposition | `tasks.md`: dependency-ordered tasks, owners (role), files touched, acceptance tests | No task > ~1 day; dependencies explicit |
| 5 | Implementation | code + tests per task (small PRs) | CI green, self-review against checklists |
| 6 | Automated testing | `test-plan.md` mapping acceptance criteria -> tests, evidence of runs | All criteria mapped; skipped tests justified |
| 7 | Security review | `security-review.md` using `api-security-review.md` | Independent reviewer signs; risks tracked |
| 8 | Performance review | `perf-review.md`: baseline vs after, method, caveats | Within budget or accepted exception |
| 9 | Documentation | docs/OpenAPI/runbooks/ADR updated | Docs reviewed |
| 10 | Release readiness | `release.md` from `release-readiness.md` + rollout/rollback | Human go/no-go |

**Rules**
* The spec is the source of truth; if implementation reveals a spec error, change the spec in the same PR and say so.
* An assistant may *draft* any artifact but a human **approves** phases 1-3, 7 and 10. Agents never approve their own high-risk work.
* Keep artifacts short; link to code and tests instead of duplicating them.
* `specs/001-ssrf-destination-guard/` shows all ten artifacts for a feature that exists in this repository (written retrospectively and labelled as such);
  `specs/feature-quota-policy.md` is a forward-looking spec for a **new gateway policy type** (daily quota) that is intentionally not implemented.

**Prompt skeleton for an assistant at any phase**
```
Role: <spec author | architect | backend | frontend | tester | security | perf | docs>
Load: ai-engineering/project-context.md + <modules>
Input artifacts: <paths>   Output artifact: <path>
Constraints: follow architecture-rules.md; no secrets; cite files/lines; mark unknowns as questions.
Done when: <gate from the table>. Report: what you produced, what you verified (commands + results), open questions.
```
