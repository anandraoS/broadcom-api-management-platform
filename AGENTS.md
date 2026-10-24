# Agent instructions (tool-neutral)

Applies to any coding agent or assistant working in this repository. Adapt this file to the instruction mechanism of your tool.

**Context loading (token-efficient):** always read `ai-engineering/project-context.md`. Load other files in `ai-engineering/` only for the task at hand (see the table in `ai-engineering/README.md`). Use `ai-engineering/repo-map.md` to find files instead of scanning the tree.

**Before editing:** open the files named under "Inspect-before-change" in the project context for the area you touch; check existing tests for the pattern to follow.

**While editing:** keep module boundaries (gateway and control-plane never import each other); follow `docs/governance/` standards; add tests (negative and failure paths); keep config environment-driven.

**Before finishing:** run the relevant commands (`mvn test`, `mvn verify`, `mvn -Pstatic-analysis compile spotbugs:check`; frontend `npm run lint`, `npm test -- --watch=false --browsers=ChromeHeadless`, `npm run build`), then report exactly what ran and its result; list anything skipped or not runnable.

**Prohibited without explicit human approval:** pushing to remotes, deleting data/branches, changing security controls, adding dependencies with network access at runtime, committing secrets, disabling tests or checks to make a build pass.

**Honesty:** do not claim success you did not observe; do not invent identifiers, scores, versions or numbers; mark hypotheses as hypotheses.

**Escalate to a human** when: a requirement is ambiguous in a security-relevant way, an architectural invariant must change, a check cannot be run and the risk is non-trivial, or two plausible designs have different operational costs.
