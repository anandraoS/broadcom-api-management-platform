# T-101: Add `dailyQuota` fields to PolicyConfig and PolicyValidator  *(ILLUSTRATIVE sample task - not executed)*

- **Spec:** `specs/feature-quota-policy.md` (AC 3, 5.API contract)
- **Role / owner:** R3 Java backend. **Depends on:** spec approved (G1), design approved (G2).
- **Context to load:** `project-context.md`, `architecture-rules.md`, `java-spring-review.md`.
- **Inspect first:** `policy/PolicyConfig.java`, `policy/PolicyValidator.java`, `PolicyValidatorTest`, gateway `config/Snapshot.java`, `SnapshotPoller.problem()`.
- **Files you may change:** `control-plane/.../policy/PolicyConfig.java`, `PolicyValidator.java`, `control-plane/src/test/.../PolicyValidatorTest.java`, `gateway/.../config/Snapshot.java` (additive fields only).
- **Must not touch:** migrations, security package, gateway filter pipeline (that is T-102).
- **Acceptance:** `dailyQuota` null or 1..1,000,000,000 accepted; others rejected with a clear message; `quotaFailMode` defaults OPEN; old policy JSON without the fields still deserializes (test); gateway ignores unknown fields.
- **Evidence to return:** `mvn -pl control-plane test` and `mvn -pl gateway test` summaries; diff stat; any skipped test and why.
- **Escalate if:** the validator would need to know about Redis or time zones; the contract change looks breaking.
