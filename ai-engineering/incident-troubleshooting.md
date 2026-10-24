# Incident troubleshooting module (for assistants helping an on-call engineer)

**Rules:** evidence before hypotheses; separate **verified facts** from **hypotheses** in every answer; never run destructive commands (restart, scale to 0, delete, kubectl exec into prod, revoke) without explicit human approval; never print secret values; label any AI-generated hypothesis as such.

**Procedure**
1. Restate the symptom, start time and scope (which APIs/consumers) - ask for missing facts.
2. Check recent changes first (audit trail, deploy history, policy versions): `GET /api/v1/audit`, Incidents page, `helm history`.
3. Use the deterministic findings (`/api/v1/incidents`): they contain evidence and fixed steps - quote them, do not paraphrase them into certainty.
4. Pick the playbook: `docs/runbooks/incident-response.md`, `control-plane-outage.md`, or `docs/troubleshooting/01..10`.
5. Collect evidence with read-only commands (metrics, logs by correlation id, traces); list which command produced which fact.
6. Propose the safest mitigation (revert/rollback/disable before fix), its blast radius and rollback; wait for approval.
7. After mitigation: confirm with SLO metrics, then draft the post-incident review from the template.

**Answer format:** `Verified facts` (with source) / `Hypotheses (ranked, with what would confirm or refute each)` / `Next safe step` / `Needs human approval`.
