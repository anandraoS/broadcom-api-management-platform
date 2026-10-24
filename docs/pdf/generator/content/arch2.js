'use strict';
const D = require('../lib/diagrams');

module.exports = [
  // ---------------------------------------------------------------- 15
  { h1: '15. CI/CD pipeline' },
  { diagram: D.cicd, h: 80, cap: 'Pipeline stages 1-6 (GitHub Actions, `.github/workflows/ci.yml`).' },
  { diagram: D.cicd2, h: 80, cap: 'Pipeline stages 7-12: scanning, images, Kubernetes validation, reports and versioned artifacts.' },
  { table: { head: ['Stage', 'Job', 'Executed locally?'], widths: [2.2, 3.6, 2.2], rows: [
    ['1-3 Java compile, unit, integration', 'mvn compile / test / verify (Failsafe)', 'Yes (Docker-dependent ITs skipped)'],
    ['4-5 Angular lint, tests, build', 'npm run lint / test:ci / build', 'Yes (Edge headless instead of Chrome)'],
    ['6 Static analysis', 'SpotBugs profile; CodeQL', 'SpotBugs yes (0 high-confidence); CodeQL no'],
    ['7 Dependency scanning', 'Trivy fs, CycloneDX SBOM', 'npm audit yes; Trivy/SBOM no'],
    ['8-9 Images', 'buildx build, hadolint, Trivy image', 'No (no Docker)'],
    ['10 Helm/K8s', 'helm lint, helm template | kubeconform, compose config', 'No (YAML syntax only)'],
    ['11-12 Reports / artifacts', 'upload Surefire/JaCoCo/Karma coverage, versioned jars/dist/chart', 'Jars and reports yes'] ] } },
  { note: 'The pipeline was written and reviewed but has **never run on a CI runner**; expect small fixes on first execution. Least-privilege token permissions, pinned major action versions and no production credentials are by design; a deploy stage is intentionally left out (needs cluster credentials via OIDC federation).' },

  // ---------------------------------------------------------------- 16
  { h1: '16. Security threat model' },
  { table: { head: ['Threat', 'Attack', 'Primary controls', 'Residual risk'], widths: [1.5, 2.2, 3.4, 2.3], rows: [
    ['Unauthorized access', 'Forged/expired token or key', 'JWT signature+issuer+exp, hashed keys, generic 401', 'Dev HS256 secret shared'],
    ['BOLA (API1)', 'Owner of team A edits team B', 'AccessGuard in service layer; scoped sub-resources', 'Consumers not team-scoped (gap)'],
    ['Credential leakage', 'Keys in logs/UI/repo', 'Shown once, hash at rest, masked URLs, no-store, .env ignored', 'Dummy dev defaults in local profile'],
    ['Malicious route config', 'Target = metadata IP / internal host', '3-layer allowlist + blocked ranges, audited rejections', 'DNS-rebinding window'],
    ['SSRF via gateway', 'Proxy abuse', 'No redirects, connect-time re-resolve, NetworkPolicy egress', 'Private-IP names allowlisted by design'],
    ['Denial of service', 'Floods, slow clients, big bodies, retry storms', 'Limits, bulkhead, body/response caps, timeouts, retry budget, breaker', 'Volumetric DDoS needs CDN/WAF'],
    ['Info exposure', 'Verbose errors, label/PII leakage', 'RFC 7807 w/o internals, bounded labels, no payload logging', 'Logs hold correlation ids'],
    ['Compromised dependency', 'Malicious/vulnerable library or image', 'BOM pinning, Trivy, SBOM, non-root minimal images', 'Maven scan only in CI'],
    ['Misconfigured Kubernetes', 'Privileged pods, open network', 'PSA restricted, drop caps, read-only FS, default-deny', 'Env-var secrets visible to exec'],
    ['Audit tampering', 'Edit history', 'Append-only entity, same-transaction write', 'DB admin can alter; ship to WORM'] ] } },
  { p: 'OWASP Top 10 (2021) and API Security Top 10 (2023) mappings are in `docs/security.md`; the security regression tests span `DestinationValidatorTest`, `PolicyValidatorTest`, `ControlPlaneApiIT`, `GatewayRoutingIT`, `PathSafetyTest` and the Angular interceptor/guard specs. **No external penetration test or secret-scanning tool was run.**' },

  // ---------------------------------------------------------------- 17
  { h1: '17. Performance investigation workflow' },
  { diagram: D.perfflow, h: 205, cap: 'Investigation workflow: revert recent changes first, then split gateway vs backend time and walk the resource checks with evidence.' },
  { h3: 'Measured results (laptop, shared CPU - relative only)' },
  { table: { head: ['Scenario (32 workers)', 'req/s', 'p50', 'p95', 'p99'], widths: [4, 1, 1, 1, 1], rows: [
    ['Backend directly', '1,774', '10.3 ms', '50.2 ms', '140.3 ms'],
    ['Through gateway (3 runs)', '919 / 1,204 / 1,353', '25.2 / 16.3 / 12.9 ms', '88.5 / 84.1 / 85.9 ms', '149.6 / 177.8 / 194.8 ms'],
    ['Gateway rejection: 401 (no key)', '3,446', '4.9 ms', '36.0 ms', '89.0 ms'],
    ['Gateway rejection: 404', '4,444', '3.4 ms', '22.4 ms', '90.7 ms'],
    ['Control plane list (16 workers, H2)', '577', '19.2 ms', '75.9 ms', '127.5 ms'] ] } },
  { warn: 'Client, gateway, control plane, two backends and the OS shared one 8-CPU laptop; closed-loop load; spread between runs was ~30%. k6 scripts exist but were **not executed**; no MySQL/Redis numbers were measured. Do not quote these as capacity. Raw output: `docs/validation/perf-*.txt`.' },
  { ul: ['**CPU saturation:** profile (JFR/async-profiler), scale gateways (HPA).', '**Memory/GC:** heap-after-GC trend, heap dump, allocation profile; every map/cache is bounded.', '**Pool exhaustion:** `hikaricp_connections_pending`, slow-query log, fail-fast 3 s acquire timeout.', '**Thread starvation:** virtual threads rarely starve; check pinning (`-Djdk.tracePinnedThreads`) and semaphore saturation.', '**Cardinality:** TSDB status, `count by (__name__)`, remove or aggregate labels.'] },

  // ---------------------------------------------------------------- 18
  { h1: '18. API and GraphQL examples' },
  { h3: 'Register, secure and call an API (all against the local profile with dummy credentials)' },
  { code: '# 1. token (dev identity provider)\ncurl -s -X POST localhost:8080/api/v1/auth/token -H "Content-Type: application/json" \\\n     -d \'{"username":"admin","password":"local-demo-password"}\'\n\n# 2. create an API (idempotent)\ncurl -s -X POST localhost:8080/api/v1/apis -H "Authorization: Bearer $T" -H "Idempotency-Key: create-billing-0001" \\\n     -H "Content-Type: application/json" -d \'{"slug":"billing","name":"Billing","ownerTeam":"platform"}\'\n\n# 3. unsafe target is rejected (SSRF) -> 422 unsafe_destination\ncurl -s -X POST localhost:8080/api/v1/apis/$ID/versions/$VID/targets -H "Authorization: Bearer $T" \\\n     -H "Content-Type: application/json" -d \'{"url":"http://169.254.169.254","weight":1}\'\n\n# 4. call through the gateway\ncurl -i -H "X-API-Key: kdemo.local-demo-only-key-0123456789abcdef" localhost:8081/gw/orders/v1/orders' },
  { h3: 'Policy document (versioned, validated before activation)' },
  { code: '{ "auth": "API_KEY", "allowedMethods": ["GET","POST"], "rateLimitPerMinute": 120, "burst": 20,\n  "rateAlgorithm": "TOKEN_BUCKET", "timeoutMs": 3000, "maxRequestBytes": 65536,\n  "ipAllowCidrs": null, "retryEnabled": true }\n# POST /api/v1/policies/validate -> {"valid":true,"errors":[]}   (dry run, no side effects)' },
  { h3: 'Standard error (RFC 7807 + code + correlation id)' },
  { code: '{ "type":"about:blank","title":"Unprocessable Entity","status":422,"detail":"Policy validation failed",\n  "code":"invalid_policy","correlationId":"9c5f5ab1-9981-46f3-874a-6db5d91065a7",\n  "errors":["auth=NONE is only allowed with read-only methods (GET, HEAD, OPTIONS)"] }' },
  { h3: 'GraphQL: one round trip for the console view' },
  { code: 'query { apis(size: 5) { totalElements content { slug lifecycle versions { label basePath targets { url weight } } } }\n        healthSummary(windowMinutes: 15) { totalRequests errorRate routes { route requests errors5xx avgLatencyMs } } }' },
  { p: '**REST vs GraphQL here:** GraphQL is read-only and reuses the same service-layer authorization; state-changing operations stay REST where idempotency keys, optimistic locking and approvals have clear semantics (`docs/rest-graphql-protocols.md`).' },

  // ---------------------------------------------------------------- 19
  { h1: '19. Architecture Decision Records' },
  { table: { head: ['ADR', 'Decision', 'Main trade-off', 'Review trigger'], widths: [0.8, 3.1, 3, 2.6], rows: [
    ['0001', 'Separate control plane and data plane', 'Eventual consistency vs isolation/availability', 'Need <1 s revocation; >50 gateways'],
    ['0002', 'Custom gateway on Spring MVC + virtual threads', 'Own the edge cases (buffering, HTTP/1.1) vs simplicity and testability', 'Streaming/WebSocket/h2 needs'],
    ['0003', 'Pull-based versioned snapshots + last-known-good', 'Bounded delay vs robustness', 'Fleet >100 or snapshot >5 MB'],
    ['0004', 'MySQL + Flyway, no shared cache in front', 'Single-writer simplicity vs scale-out', 'Registry QPS / snapshot build > 100 ms'],
    ['0005', 'OIDC/JWT for people, hashed API keys for consumers', 'Dev HS256 convenience vs production parity', 'Client-credentials needs; mTLS mandate'],
    ['0006', 'Timeout budget, safe retries, consecutive-failure breaker, bulkhead', 'Simplicity vs partial-failure sensitivity', 'Missed partial failures'],
    ['0007', 'Telemetry as batched idempotent HTTP, no broker', 'Possible loss vs zero extra infrastructure', 'Replay/multi-consumer analytics'],
    ['0008', 'Destination allowlist at three layers', 'Operational friction vs SSRF defence in depth', 'Many namespaces; rebinding evidence'],
    ['0009', 'Angular standalone, signals + RxJS', 'Token in sessionStorage vs simplicity', 'Shared state growth; Node upgrade'],
    ['0010', 'Versioning and compatibility rules', 'Cost of multiple versions vs predictability', '>2 live majors'] ] } },
  { p: 'Each ADR (in `docs/adr/`) records context, decision, alternatives, consequences and concrete review triggers; a blank template is in `docs/governance/adr-template.md`.' },

  // ---------------------------------------------------------------- 20
  { h1: '20. Technical-leadership workflows' },
  { ul: ['**Feature incubation to delivery:** problem framing -> spec with acceptance criteria -> ADR for invariants -> vertical slices -> reviews -> release readiness -> post-release review. Roadmap, epics and cross-team dependencies: `docs/leadership/delivery-roadmap-and-epics.md`.', '**Review culture:** code-review checklist, architecture-review scenarios, AI-assisted PR disclosure (`docs/leadership/review-scenarios.md`).', '**Mentoring:** evidence-first coaching for recurring performance mistakes, security-change onboarding, review of AI-generated work (`docs/leadership/mentoring-examples.md`).', '**Communication:** weekly stakeholder update and release-risk summary templates that separate facts, estimates and unknowns.', '**Ownership:** the production-readiness assessment states plainly what is ready, partial or a gap - including the facts that Docker, Kubernetes, Helm and CI were never executed here.', '**Learning loop:** blameless post-incident review template with action types (prevent/detect/mitigate/process).'] },
  { table: { head: ['Phase', 'Theme', 'Exit criteria'], widths: [1.4, 3, 3.6], rows: [
    ['0 Incubation', 'Framing, ADRs, walking skeleton', 'Scope and non-goals reviewed'],
    ['1 MVP', 'Safe routing', 'Smoke + e2e green, threat model reviewed, one pilot API'],
    ['2 Hardening', 'Operate it', 'Real OIDC, HA data stores, game day passed, SLOs agreed'],
    ['3 Scale', 'Self-service, quotas', '10+ APIs, onboarding < 1 day'],
    ['4 Insight', 'Tail sampling, evidence-first analytics', 'Measured MTTR improvement'] ] } },

  // ---------------------------------------------------------------- 21
  { h1: '21. AI-assisted development lifecycle' },
  { p: 'AI assistants are used as **engineering collaborators inside a controlled loop**, not as one-off prompt generators: the repository carries reusable context, review modules and a verification discipline so any assistant (or human) can contribute safely.' },
  { ol: ['**Context:** load `project-context.md` (short) + only the modules the task needs.', '**Specify:** write or update the spec (acceptance criteria, security, failure behaviour, rollout).', '**Implement in small tasks** with named files and tests.', '**Verify with real commands** (tests, static analysis, smoke/e2e) and record the output.', '**Review independently** against the checklists; treat generated code as untrusted.', '**Human approval** for specs, designs, security-sensitive changes and releases.', '**Capture learning:** update the module/checklist when a review finds a new failure mode.'] },
  { warn: 'Honesty statement: in building this repository a **single assistant session** acted in every role sequentially; no parallel multi-agent execution took place. Two real defects were found by live runs rather than reviewed away (telemetry unique key; SPA route captured by `/api` proxy prefix) - each was fixed and is guarded by a test.' },

  // ---------------------------------------------------------------- 22
  { h1: '22. Specification-driven development' },
  { diagram: D.specflow, h: 85, cap: 'Ten-phase workflow (`ai-engineering/spec-driven-workflow.md`); humans approve the first three phases, security review and release.' },
  { ul: ['**Artifacts** live in `specs/<nnn>-<name>/` (prd, requirements, design, tasks, test-plan, security-review, perf-review, release).', '**Sample feature spec** for a new gateway policy type - daily request quota - with every required section: objective, stories, acceptance criteria, API contract, data model, security, failure behaviour, observability, performance expectations, test scenarios, rollout/rollback (`specs/feature-quota-policy.md`; intentionally not implemented).', '**Worked retrospective example:** `specs/001-ssrf-destination-guard/` shows all ten artifacts for a real feature, labelled retrospective.', '**Tool independence:** BMAD-style role phases or spec-kit style `specify/plan/tasks` map onto the same artifacts; names evolve, the artifacts are the contract.'] },

  // ---------------------------------------------------------------- 23
  { h1: '23. Multi-agent orchestration' },
  { diagram: D.multiagent, h: 215, cap: 'Designed multi-agent protocol: eight roles, dependency-aware sequencing, frozen shared contracts, human gates for specs/design and for security/release.' },
  { table: { head: ['Role', 'May change', 'Must not change', 'Handoff criteria'], widths: [1.5, 2.2, 2.2, 3], rows: [
    ['R3 Java backend', 'control-plane/**, gateway/** (assigned files)', 'migrations merged earlier, infra, frontend, contracts', 'mvn verify output attached; no invariant violated'],
    ['R4 Angular', 'frontend/**', 'backend, contracts', 'lint/unit/build outputs'],
    ['R5 Test engineering', 'src/test/**, e2e, performance', 'production code', 'every acceptance criterion mapped; tests fail on mutation'],
    ['R6 Security review', 'review docs only', 'production code', 'independent of author; blockers resolved or human-accepted'],
    ['R7 Perf/observability', 'observability/**, measured docs', 'production code', 'numbers with method and caveats'] ] } },
  { ul: ['Agents never overwrite shared architectural decisions (ADRs, `architecture-rules.md`) and never approve their own high-risk work; conflicts escalate to the architecture role and then a human.', 'Every handoff lists commands run and real results; unverifiable claims are marked UNVERIFIED (`ai-engineering/multi-agent/handoffs/_template.md`).', '**Sample tasks** T-101..T-103 and **sample handoff** H-101 are illustrative and say so; no multi-agent run was executed.'] },

  // ---------------------------------------------------------------- 24
  { h1: '24. Context engineering and reusable AI skills' },
  { diagram: D.context, h: 185, cap: 'Modular context: one short always-loaded file plus task-selected modules and a repository map; hand off summaries, not transcripts.' },
  { ul: ['**Reusable modules (12):** project-context, architecture-rules, java-spring-review, angular-review, api-security-review, performance-review, test-generation, database-migration-review, kubernetes-review, incident-troubleshooting, feature-spec-template, release-readiness.', '**Tool adapters:** `CLAUDE.md` (Claude Code), `AGENTS.md` (generic), `.github/copilot-instructions.md`.', '**Techniques:** repository map instead of tree scans, smallest-range file reads, tests as documentation, ADRs as the memory of decisions, handoff notes, task splitting, detecting missing context (invented APIs, unverifiable claims).', '**Before/after files:** `ai-engineering/context-examples/` (monolithic vs modular).', '**Token measurement method** (no invented savings): same tasks, same model, fresh sessions, read input/output tokens from the tool usage report, compare medians and pass rate, record dates and model versions. **No token measurements were taken for this repository.**'] },

  // ---------------------------------------------------------------- 25
  { h1: '25. Code-review and vulnerability-remediation workflows' },
  { p: 'Generated code is untrusted until reviewed and validated: assistants optimise for plausible patterns, can invent APIs, miss invariants, and write tests that mirror the implementation. `ai-engineering/review-examples/examples.md` holds twelve flawed-to-fixed examples (SQL injection, BOLA, unbounded retries, non-atomic limiter without timeouts, SSRF, secrets in logs, check-then-act race, boundary violation, unbounded read, unsafe config swap, mirror tests, over-engineering), each tied to a real regression test.' },
  { table: { head: ['Remediation item', 'Evidence (real unless marked)'], widths: [2.6, 5.4], rows: [
    ['Confirmed finding', '`npm audit`: 14 high-severity in **dev tooling only** (braces via karma/typescript-eslint, GHSA-vfj7-8cjw-p6xm); production dependencies: **0**; no non-breaking fix (only `--force` downgrading karma) -> documented acceptance with owner and expiry'],
    ['Dependency upgrade', 'java-jwt 4.4.0 -> 4.6.2 in the gateway: tests added first (nbf in future, alg:none), baseline and post-upgrade runs both green; transitive set unchanged; **performance not re-measured**; no vulnerability in 4.4.0 was claimed'],
    ['Refactoring', 'TargetSelector rewrite during review (honestly noted as refactor-in-review, not test-first)'],
    ['Illustrative only', 'A placeholder "library X < 2.9.1" scenario is clearly labelled as simulated; no CVE ids or scores are invented anywhere'],
    ['Rollback plan', 'Revert one pom line / `helm rollback`; no data migration involved'] ] } },

  // ---------------------------------------------------------------- 26
  { h1: '26. Architecture mind map' },
  { diagram: D.mindmap, h: 390, cap: 'Mind map of the platform: control plane, gateway, security, observability, resilience, data, delivery, testing and AI-assisted engineering.' },

  // ---------------------------------------------------------------- 27
  { h1: '27. Troubleshooting scenarios' },
  { table: { head: ['#', 'Scenario', 'First evidence to collect', 'Typical corrective action'], widths: [0.4, 2.2, 3.2, 2.9], rows: [
    ['1', 'Intermittent 502/504', '502 vs 504 split, TARGET_UNHEALTHY/CIRCUIT_OPEN events, backend latency vs timeoutMs', 'Remove bad target, tune timeout from p99, outlier ejection'],
    ['2', 'Latency after a release', 'p95 before/after, trace split gateway vs backend, audit/deploy history', 'Rollback; cache safe-DNS results; perf gate'],
    ['3', 'Redis limiter inconsistent', 'Redis failure counter, events, X-RateLimit headers, EXPECTED_REPLICAS vs HPA', 'Align replica config, managed Redis, CLOSED mode where required'],
    ['4', 'MySQL pool exhaustion', 'hikari pending/usage, processlist, slow log, thread dump', 'Paginate/index, short transactions, then right-size pool'],
    ['5', 'High JVM memory', 'heap after GC, class histogram, heap dump, key counts', 'Bound per-key state, fix XFF trust, size limits'],
    ['6', 'Excessive GC', 'GC logs/JFR allocation, humongous allocations', 'Pre-size/limit buffers, smaller body limits'],
    ['7', 'Broken policy deployment', 'Recent config changes, policy diff, snapshot version/age', 'Revert (new version), show per-second effective rate in review'],
    ['8', 'Trace correlation broken', 'Backend headers (/echo), propagator config, sampling', 'Strip + inject traceparent, W3C everywhere'],
    ['9', 'Pods restarting', 'describe pod last state, previous logs, secret keys, memory vs limit', 'Fix config/limits; keep startupProbe'],
    ['10', 'Upgrade regression', 'dependency:tree diff, release notes, old vs new on same inputs', 'Pin/rollback, add boundary tests, canary'] ] } },
  { note: 'All ten are exercises with synthetic root causes (`docs/troubleshooting/`), each with symptoms, hypotheses, evidence, commands, metrics/logs, root cause, corrective action, regression tests and prevention. They are not descriptions of real incidents.' },

  // ---------------------------------------------------------------- 28
  { h1: '28. Limitations and future enhancements' },
  { h3: 'Not verified in the build environment' },
  { ul: ['Docker builds, docker compose, Kubernetes manifests, Helm chart rendering/installation, the CI workflow, Grafana/Prometheus/Jaeger runs.', 'Redis Lua scripts and MySQL migrations on real servers (Testcontainers tests exist and auto-skip without Docker).', 'k6 load tests (scripts only); MySQL/Redis performance; production-scale capacity.', 'Live OIDC integration; penetration testing; Maven vulnerability scanning; backup/restore and failover drills.'] },
  { h3: 'Known design limitations' },
  { ul: ['Gateway buffers bodies (no streaming/SSE/WebSocket); HTTP/1.1 upstream only; HTTP/2/3 are conceptual.', 'Consumers are not team-scoped; no developer portal; daily quotas specified but not implemented.', 'Dashboard stores sum/max per minute only; percentiles come from Prometheus.', 'Per-request DNS re-check costs latency on slow resolvers; consecutive-failure breaker misses interleaved partial failures.', 'Gaps in tests: database-failure mid-request, telemetry shipper retry/drop logic, graceful shutdown.'] },
  { h3: 'Future enhancements' },
  { ul: ['OIDC + mTLS between components; signed snapshots; tail sampling at an OpenTelemetry Collector; retry budgets and outlier ejection; canary policy rollout per gateway group.', 'Quota plans, consumer portal, per-team consumer scoping, WebSocket/streaming routes (possibly via a second data-plane engine).', 'Pre-install migration Job, GitOps (Argo CD), policy-as-code checks in CI, chaos drills as release gates.', 'Token-usage measurements for the AI context modules and measured productivity/quality comparisons.'] },
];
