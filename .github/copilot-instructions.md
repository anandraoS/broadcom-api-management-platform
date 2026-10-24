# Copilot instructions

Read `ai-engineering/project-context.md`; load other `ai-engineering/*.md` modules only when relevant. Ten hard rules:

1. No secrets or real data anywhere (code, tests, docs, prompts).
2. gateway and control-plane must not import each other; contract is JSON in `docs/api-contracts.md`.
3. Deny by default; role AND object-level authorization in the service layer.
4. Parameterised queries only; validate and bound all input.
5. Outbound URLs only from the allowlisted registry; never follow redirects.
6. Every outbound call has timeouts; retry only GET/HEAD/OPTIONS with backoff + jitter.
7. Caches, queues, buffers and per-key state are bounded.
8. Migrations are Flyway, expand/contract, never edited after merge.
9. Add tests including negative/failure paths; report real results; never claim unrun checks passed.
10. Do not push, delete or disable checks without human approval.
