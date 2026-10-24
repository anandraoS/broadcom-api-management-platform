# Definition of Done

A change is **done** only when all applicable items are true (and the PR says which ones are N/A and why):

**Build and quality**
- [ ] Compiles; `mvn verify` and `npm run lint && npm test && npm run build` pass in CI
- [ ] New/changed behaviour has automated tests, including negative and failure paths; bug fixes have a regression test
- [ ] Static analysis clean (`-Pstatic-analysis`); no new lint warnings; no skipped tests without a tracked reason

**Security**
- [ ] `secure-coding-checklist.md` completed; threat model row updated if a new input/trust boundary was added
- [ ] No secrets or real data anywhere (code, tests, docs, AI context); dependency scan reviewed

**Operability**
- [ ] Metrics/logs/traces for new behaviour; alerts and runbook lines for new failure modes
- [ ] Config is environment-driven with safe defaults; Helm/compose/k8s updated if config changed
- [ ] Migration reviewed with `db-migration-checklist.md`; rollback documented

**Documentation and review**
- [ ] OpenAPI/contract docs, README/ADR updated; changelog entry
- [ ] Reviewed by a second engineer (CODEOWNER for security/gateway/infra/migrations); AI-assisted parts disclosed and verified
- [ ] Performance impact considered (`performance-review-checklist.md`); hot-path changes have before/after numbers

**Release**
- [ ] Rollout plan (flag/canary/propagation window) and rollback steps written
- [ ] Release readiness checklist signed for releases (`release-readiness.md`)
