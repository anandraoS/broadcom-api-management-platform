# CI/CD pipeline (`.github/workflows/ci.yml`)

| # | Stage | Job / command | Local evidence |
|---|---|---|---|
| 1 | Java compilation | `mvn -B -DskipTests compile` | executed (BUILD SUCCESS) |
| 2 | Java unit tests | `mvn test` (Surefire: `*Test`) | executed |
| 3 | Integration tests | `mvn verify` (Failsafe: `*IT`; Testcontainers ITs auto-skip without Docker) | executed (Docker-dependent ITs skipped) |
| 4 | Angular lint + tests | `npm run lint`, `npm run test:ci` (Karma, `ChromeHeadlessCI`, coverage) | lint and tests executed (Edge headless locally) |
| 5 | Angular production build | `npm run build` | executed |
| 6 | Static analysis | SpotBugs (`-Pstatic-analysis`), CodeQL (Java, TS) | SpotBugs executed (0 high-confidence findings); CodeQL not run locally |
| 7 | Dependency scanning | Trivy `fs` (Maven + npm), CycloneDX SBOM | **`npm audit` executed** (see report); Trivy/SBOM not run locally |
| 8 | Image build | `docker/build-push-action` per image (not pushed) | not executed (no Docker) |
| 9 | Image scanning | Trivy image scan, hadolint | not executed |
| 10 | Helm/K8s validation | `helm lint`, `helm template | kubeconform`, `docker compose config` | YAML syntax checked locally only |
| 11 | Reports | upload Surefire/Failsafe/JaCoCo/Karma coverage artifacts | JaCoCo/Surefire reports generated locally |
| 12 | Versioned artifacts | jars named `<module>-1.0.0.jar` + git SHA artifact name, frontend dist, Helm chart `1.0.0` | jars built locally |

Design rules: least-privilege `permissions: contents: read`; pinned major versions of actions (pin to SHAs in production); no secrets
needed (tests use H2/dummy config); concurrency group cancels superseded runs; the release job runs only on `main` after all gates; a
deploy stage (Helm `--atomic`) is intentionally omitted from the default pipeline because it requires cluster credentials - add it
behind an OIDC-federated role and an approval environment.

**Branch protection (recommended):** required checks = stages 1-10, signed commits, CODEOWNERS review for `control-plane/**/security`,
`gateway/**`, `infrastructure/**`, `db/migration/**`; no force pushes to `main`.
