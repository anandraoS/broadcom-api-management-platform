# Post-incident review (blameless)

**Title / ID:** ... **Date and duration:** ... **Severity:** ... **Incident commander / scribe:** ... **Status:** draft | reviewed | actions tracked

## Summary
Two or three sentences: what happened, who/what was affected, how it ended.

## Impact
Customers/APIs affected, error budget consumed, revenue/contract impact if any, data exposure (yes/no, scope).

## Timeline (UTC)
| Time | Event | Source (dashboard/log/chat) |
|---|---|---|
| hh:mm | First symptom / alert | |
| hh:mm | Detection | |
| hh:mm | Mitigation started / completed | |
| hh:mm | Resolution confirmed | |

## Detection
How did we find out (alert, customer, engineer)? Time to detect. Which signal should have fired earlier?

## Root cause and contributing factors
Direct cause; latent conditions (missing validation, config drift, unclear ownership); why existing safeguards did not stop it (use the "five whys", stop at systems, not people).

## What went well / what was lucky / what went poorly

## Response analysis
Time to mitigate vs time to resolve; was rollback available and fast? Was communication clear? Were runbooks accurate?

## Action items
| Action | Type (prevent / detect / mitigate / process) | Owner | Due | Tracking link |
|---|---|---|---|---|
Each action must be specific and verifiable (e.g. "add alert for pool pending > 0 for 2 min", not "improve monitoring").

## Evidence
Dashboards, queries, correlation ids/traces, audit events (`/api/v1/audit`), config versions (policy v N -> N-1), commands run.

## Lessons shared
Where this will be presented; which docs (runbooks, checklists, ADR review triggers) were updated.
