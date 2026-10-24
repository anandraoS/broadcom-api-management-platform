# AFTER: modular index with pointers (what `CLAUDE.md` / `AGENTS.md` do)

Always load: `ai-engineering/project-context.md` (about 60 lines: purpose, module boundaries, commands, hard constraints, invariants, DoD, inspect-before-change).

Load on demand (task -> module): Java change -> `architecture-rules.md` + `java-spring-review.md`; security -> `api-security-review.md`; schema -> `database-migration-review.md`; UI -> `angular-review.md`; infra -> `kubernetes-review.md`; perf -> `performance-review.md`; tests -> `test-generation.md`; incident -> `incident-troubleshooting.md`; feature -> `feature-spec-template.md`; release -> `release-readiness.md`.

Find files: `ai-engineering/repo-map.md` (do not scan the tree). Decisions: `docs/adr/` (read the one you need). Facts that are verified: `docs/validation/`.

Hard rules (5 lines): no secrets/real data; read before edit; run tests and report real results; no push/destructive actions without approval; never invent versions/IDs/numbers.

Session handoff (instead of transcripts): `multi-agent/handoffs/_template.md`.
