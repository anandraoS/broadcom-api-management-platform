# Release readiness module (for assistants preparing a release summary)

Authoritative checklist: `docs/governance/release-readiness.md`. When asked "are we ready to release?":

1. Gather **evidence**, do not assume: latest CI result, `mvn verify` / `npm` outputs (run them if allowed), scan outputs, migration list since the last tag, contract diff, performance numbers vs `docs/performance.md`.
2. Fill the table row by row with `Ready / Not ready / Unknown` and the evidence link or command output. `Unknown` is a valid, honest answer.
3. List **skipped/blocked checks** explicitly (e.g. Testcontainers ITs without Docker, Helm render without helm, CI never run).
4. Summarise risks in plain language: what could go wrong, blast radius, detection, rollback command, owner.
5. Recommend go / no-go but state that **a human approves** release decisions; never mark approvals as given.
6. Draft the release notes and stakeholder message from the templates in `docs/leadership/stakeholder-and-release-communication.md`.

Never claim: tests passed, scans clean, benchmarks met, or deployments succeeded unless the output exists in this session or an attached CI run.
