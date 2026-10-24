# 10 - A dependency upgrade causes regressions

**Symptoms:** after bumping a library, JWT-protected routes return 401 for valid tokens in staging (exercise); tests were green because they only used HS256 fixtures with default claims.

**Hypotheses:** (1) behaviour change (stricter claim/`typ`/`nbf` validation, different default clock skew); (2) transitive dependency changed (Jackson/Nimbus/Netty versions); (3) deprecated API removed; (4) serialization format change (dates as numbers vs strings); (5) performance regression (allocation); (6) licence/CVE-driven pin conflicts with the Spring Boot BOM.

**Evidence:** `mvn dependency:tree -Dincludes=...` before/after, release notes/changelogs, `mvn -Pstatic-analysis`, diff of `target/classes`-level behaviour via tests; run old vs new on the same token set; compare serialized output; load test p95.

**Commands**
```bash
mvn -B dependency:tree -pl gateway | grep -E 'jwt|jackson'
mvn -B versions:display-dependency-updates -pl gateway          # candidates only; do not blindly accept
git bisect run mvn -q -pl gateway test -Dtest=GatewayRoutingIT
```

**Root cause (exercise):** the new JWT library version validates the `exp` claim with zero leeway by default and rejects tokens signed by an IdP whose clock is a few seconds ahead; the gateway test token generator used the same clock, hiding it.
**Corrective:** explicitly configure leeway (`acceptLeeway(5)`), add tokens with `nbf`/`exp` skew to the fixtures; pin the dependency until verified; roll back via the lock/BOM revision.
**Regression tests:** `jwtPolicyVerifiesSignatureIssuerExpiryAndRole` (expired, wrong issuer, forged) + add `nbf`/skew cases; contract tests for the snapshot format.
**Prevention:** the upgrade workflow in `docs/governance/vulnerability-remediation.md` (a **real** `java-jwt` upgrade executed in this repository is recorded there with test results), Dependabot/Renovate PRs with CI gates, canary rollout, release notes review, keep security-sensitive libraries behind small adapters (`Authenticator`) so behaviour is tested at our boundary.
