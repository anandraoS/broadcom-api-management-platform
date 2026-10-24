# Claude Code instructions for this repository

Read @ai-engineering/project-context.md first (it is short). Load further modules **only when the task needs them** - do not paste the whole `ai-engineering/` folder into context:

- Java/Spring change: @ai-engineering/architecture-rules.md and @ai-engineering/java-spring-review.md
- Security-relevant change: @ai-engineering/api-security-review.md
- Tests: @ai-engineering/test-generation.md - Schema: @ai-engineering/database-migration-review.md
- Angular: @ai-engineering/angular-review.md - Helm/Docker/K8s: @ai-engineering/kubernetes-review.md
- Hot path/perf: @ai-engineering/performance-review.md - Production issue: @ai-engineering/incident-troubleshooting.md
- New feature: @ai-engineering/feature-spec-template.md and `ai-engineering/spec-driven-workflow.md` - Release: @ai-engineering/release-readiness.md

Hard rules
1. No secrets, tokens, real customer data in code, tests, docs, logs or prompts. All credentials here are dummy local-only values.
2. Read the files listed under "Inspect-before-change" in project-context before editing; prefer reading to guessing.
3. Run the relevant tests after changes (`mvn test` / `mvn verify`, `npm run lint && npm test -- --watch=false --browsers=ChromeHeadless`) and report real results; say when a check was skipped or could not run (e.g. no Docker).
4. Do not run `git push`, destructive commands, or production-affecting operations without explicit human approval.
5. Never invent APIs, versions, CVE ids, benchmark numbers or test outcomes.
6. Architectural invariants are in `architecture-rules.md`; changing one requires a new ADR and human approval.
7. Keep diffs small; add or update tests; follow `docs/governance/*` standards.
