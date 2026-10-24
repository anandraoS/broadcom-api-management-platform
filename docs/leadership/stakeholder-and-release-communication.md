# Stakeholder updates and release-risk communication

## Weekly stakeholder update (template with an example)
**Status:** Green / Amber / Red - one sentence why.
**Done this week:** outcomes, not tasks (e.g. "approvals now enforced end to end with audit; gateway applies new policy within ~7 s locally").
**Next week:** top 3 outcomes.
**Risks and decisions needed:** each with owner and date (e.g. "OIDC claims contract needed from Identity by Oct 30 or Phase 2 slips 1 week").
**Metrics:** test counts/pass rate, defect trend, SLO burn (once live), performance vs baseline.
**Honest caveats:** what is *not* verified (e.g. "Docker/Kubernetes assets validated by review only; first cluster run planned in sprint 7").

## Release-risk communication
Plain-language, decision-oriented, quantified where possible.

> **Release 1.1 - risk summary (example).**
> *What changes:* new daily-quota policy type; migration V3 adds one nullable column (online).
> *Who is affected:* API owners (new option in the editor); no change for existing consumers.
> *Risks:* (1) policy propagation takes up to ~35 s, so a bad quota can affect traffic for that long before revert completes - mitigated by dry-run validation, four-eyes review, one-click revert; (2) Redis quota keys add one round trip per request - **[fill in: measured p95 delta from the staging load test; do not publish an estimate as a measurement]**.
> *Rollback:* helm rollback to 1.0 (data compatible); estimated 10 minutes; owner: on-call SRE.
> *Decision requested:* go/no-go on Thursday 14:00; fallback date Tuesday.

Rules: lead with impact and decision; separate facts, estimates and unknowns; never hide a known failing check - state it and the mitigation; confirm receipt for partner-facing notices; after the release send the outcome versus prediction.
