# AI-assisted engineering workspace

Reusable, tool-independent instructions that let a coding assistant (Claude Code, Copilot, Cursor, a custom agent) work *inside this repository* without
re-reading everything or inventing facts. **Modular on purpose:** load `project-context.md` always, then only the module(s) relevant to the task.

> Honesty statement: this workspace is *configuration and process*. During the build of this repository a **single** assistant session performed every
> role sequentially; no parallel multi-agent run took place. The multi-agent files describe a controlled workflow and contain *sample* tasks/handoffs,
> clearly labelled as examples (`multi-agent/`). Named methods (BMAD-style, spec-kit style) evolve; the artifacts here do not depend on any tool.

## Always load
`project-context.md` (about 60 lines) - overview, module boundaries, commands, invariants, Definition of Done, "inspect before you change" rules.

## Load by task

| Task | Load these modules |
|---|---|
| Any Java/Spring change | `architecture-rules.md`, `java-spring-review.md` |
| Auth, policy, SSRF, secrets | `api-security-review.md` |
| Gateway hot path / limiter / proxy | `performance-review.md`, `architecture-rules.md` |
| New/changed tests | `test-generation.md` |
| Schema change | `database-migration-review.md` |
| Angular work | `angular-review.md` |
| Helm / manifests / Docker | `kubernetes-review.md` |
| Production issue | `incident-troubleshooting.md` |
| New feature | `feature-spec-template.md` then `spec-driven-workflow.md` |
| Release | `release-readiness.md` |
| Reducing context / token use | `context-engineering-guide.md` |
| Several agents | `multi-agent/README.md` |
| Reviewing AI output | `review-examples/README.md` |

## Tool adapters (all point at the same modules)

| Tool | File | Notes |
|---|---|---|
| Claude Code | `/CLAUDE.md` (+ `.claude/` for local settings, not committed with secrets) | imports `project-context.md`; lists the on-demand modules |
| Generic agents (Codex, Aider, others reading `AGENTS.md`) | `/AGENTS.md` | same content, tool-neutral phrasing |
| GitHub Copilot | `.github/copilot-instructions.md` | pointer + the 10 hard rules |
| Anything else | copy `AGENTS.md` into the tool's instruction slot | |

## Rules for everyone (human or agent)
1. Never put secrets, real customer data or credentials in prompts, context files, specs, tests or logs.
2. Generated code is **untrusted until reviewed and validated** by running the tests and reading the diff.
3. Prefer reading files over guessing; say "I don't know" and ask rather than inventing APIs, versions, CVEs or numbers.
4. Never claim a check passed unless it was executed and passed; report skipped/blocked checks.
5. Keep changes small, reversible, covered by tests, and consistent with `architecture-rules.md`.
