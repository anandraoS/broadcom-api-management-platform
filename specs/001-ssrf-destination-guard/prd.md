# 001 - Destination guard (SSRF prevention): PRD  *(retrospective)*

**Problem:** API owners register backend URLs. Without controls a malicious or mistaken URL (cloud metadata, internal admin hosts, `file://`, credentials in URLs, redirects) turns the gateway into an internal proxy.
**Personas:** API owner (registers targets), security reviewer (owns the allowlist policy), operator (investigates rejected targets).
**Objective and metric:** 0 unapproved destinations reachable through the gateway; every rejection audited; legitimate backends registered without code changes.
**Non-goals:** egress firewalling (Kubernetes NetworkPolicy complements this), DNS pinning proxy, per-API allowlists.
**Approval (human):** security lead - required before merge (recorded in the PR in a real workflow).
