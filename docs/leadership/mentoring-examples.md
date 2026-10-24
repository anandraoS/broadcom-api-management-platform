# Mentoring examples

**Principles:** make the learner discover the failure with evidence; teach the *method* (measure -> hypothesise -> verify), not the answer; give small safe stretch tasks; review in person for the first few PRs, then asynchronously with written reasoning; celebrate caught bugs.

## 1. The engineer who repeatedly introduces performance problems
*Pattern:* three PRs added per-request DNS lookups, unbounded maps and JSON re-parsing.
1. **Diagnose together, don't scold:** pair for an hour on the profiler - run `performance/measure-with-resources.sh`, open a JFR/flame graph on the offending PR, let them find the hot method.
2. **Give a checklist, not a lecture:** `performance-review-checklist.md` added to their PR template; they self-review against it.
3. **Shift left:** they add a microbenchmark/regression test for the next hot-path change (`RouteTableTest` style) and a before/after table in the PR.
4. **Create a safe win:** assign a small optimisation ticket with a measurable target, review the numbers (not the opinion), and share it in the team demo.
5. **Follow up:** after two weeks review whether the checklist was used; adjust pairing frequency. If the pattern persists, look for systemic causes (unclear SLOs, rushed deadlines, missing tooling).

## 2. Junior engineer, first security-sensitive change
Give them the threat model row, not just the ticket; ask "what would an attacker send?"; have them write the negative tests first; walk through `AccessGuard` and the BOLA test; they present the PR's security notes to the team.

## 3. Reviewing AI-assisted work with a mentee
Teach "trust, but verify": ask for the prompt/spec, run the tests locally, read the diff for invented APIs and silent behaviour changes, delete anything they cannot explain. Pair on rewriting one generated function by hand to build intuition.

## 4. Growing a future tech lead
Delegate an ADR end-to-end (context, alternatives, review triggers); let them run the design review; coach on writing the stakeholder update and on saying "not now" with data.

## Feedback template
*Observation (specific, factual) -> impact -> question -> agreed next step -> date to review.*
