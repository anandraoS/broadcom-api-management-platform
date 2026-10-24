# BEFORE: one big instruction file (anti-pattern, shown for comparison)

We are building an API platform. It has a control plane and a gateway. The control plane is a Spring Boot app that uses MySQL and the gateway is a Spring Boot app too. The control plane has controllers and services and repositories. The gateway has a filter. Use Java 21. Use Spring Boot. Use Maven. The control plane stores APIs, versions, targets, policies, consumers, keys, audit events. The gateway reads the configuration from the control plane. (...repeated again three paragraphs later in different words...)

Rules: be secure. Don't use SQL injection. Always use parameterised queries. Don't put secrets in code. Don't put secrets in code or in tests or in logs. Make sure authorization is checked. Check roles. Check that users own things. (copy of the whole OWASP Top 10 list pasted here: ~120 lines)

Kubernetes: use Deployments, use probes, use limits, don't run as root, (full Helm values file pasted: ~250 lines)

Angular: use standalone components... (entire Angular style guide pasted: ~300 lines)

History: last month we decided X, then changed to Y, then back to X (discussion transcript, ~400 lines, no conclusion marked)

File listing (from `tree`, includes target/ and node_modules/: ~3,000 lines)

Current TODOs (several already done): ...
