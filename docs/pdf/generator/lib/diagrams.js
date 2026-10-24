'use strict';
/* Diagram functions: (doc, x, y, W, C) draw inside a W x h region whose height is declared by the caller. */

function box(doc, x, y, w, h, label, o = {}) {
  doc.save();
  doc.roundedRect(x, y, w, h, 5).fillAndStroke(o.fill || '#eaf1fb', o.stroke || '#0b5cad');
  doc.fillColor(o.color || '#1f2933').font(o.bold ? 'Helvetica-Bold' : 'Helvetica').fontSize(o.size || 8.5);
  const lines = String(label).split('\n');
  const lh = (o.size || 8.5) + 2;
  let ty = y + (h - lines.length * lh) / 2 + 1;
  lines.forEach((l) => { doc.text(l, x + 3, ty, { width: w - 6, align: 'center', lineBreak: false }); ty += lh; });
  doc.restore();
}
function arrow(doc, x1, y1, x2, y2, label, o = {}) {
  doc.save();
  doc.strokeColor(o.color || '#5f6b7a').lineWidth(1); if (o.dash) doc.dash(3, { space: 2 });
  doc.moveTo(x1, y1).lineTo(x2, y2).stroke(); if (o.dash) doc.undash();
  const a = Math.atan2(y2 - y1, x2 - x1); const s = 5;
  doc.fillColor(o.color || '#5f6b7a').moveTo(x2, y2).lineTo(x2 - s * Math.cos(a - 0.4), y2 - s * Math.sin(a - 0.4)).lineTo(x2 - s * Math.cos(a + 0.4), y2 - s * Math.sin(a + 0.4)).fill();
  if (label) { doc.fillColor('#1f2933').font('Helvetica').fontSize(7.2).text(label, Math.min(x1, x2) + (o.dx || 3), (y1 + y2) / 2 - 9 + (o.dy || 0), { width: o.lw || 120, lineBreak: false }); }
  doc.restore();
}
function text(doc, x, y, t, o = {}) { doc.save(); doc.fillColor(o.color || '#1f2933').font(o.bold ? 'Helvetica-Bold' : 'Helvetica').fontSize(o.size || 8.5).text(t, x, y, { width: o.w || 400, align: o.align || 'left', lineBreak: false }); doc.restore(); }
const G = { fill: '#e8f6ee', stroke: '#2f8f5b' }, O = { fill: '#fff1e0', stroke: '#d9822b' }, P = { fill: '#f1e9fb', stroke: '#7a4bc2' }, GR = { fill: '#eef0f3', stroke: '#8a94a3' };

const diagrams = {};

diagrams.component = (doc, x, y, W) => {
  text(doc, x, y, 'Consumers / Operators', { bold: true });
  box(doc, x, y + 14, 110, 30, 'API consumers\n(partners, apps)', GR); box(doc, x + 300, y + 14, 110, 30, 'Operators\n(Angular console)', GR);
  box(doc, x, y + 80, 110, 34, 'Ingress / TLS\n(WAF, limit-rps)', O);
  box(doc, x + 140, y + 70, 130, 130, 'GATEWAY (N replicas)\nroute table - auth\nlimiter - proxy\nbreaker - bulkhead\nsnapshot cache', { fill: '#dfeefc', stroke: '#0b5cad', bold: true });
  box(doc, x + 330, y + 80, 150, 70, 'CONTROL PLANE (M replicas)\nregistry - policies - keys\naudit - incident rules\nsnapshot publisher', { fill: '#dfeefc', stroke: '#0b5cad', bold: true });
  box(doc, x + 340, y + 175, 110, 34, 'MySQL\n(system of record)', G);
  box(doc, x + 140, y + 230, 150, 32, 'Backend services\n(allowlisted hosts only)', G);
  box(doc, x + 10, y + 175, 100, 34, 'Redis\n(rate-limit counters)', P);
  box(doc, x + 320, y + 240, 150, 30, 'Prometheus - Grafana - Jaeger', P);
  arrow(doc, x + 55, y + 44, x + 55, y + 80); arrow(doc, x + 110, y + 97, x + 140, y + 97);
  arrow(doc, x + 355, y + 44, x + 380, y + 80); arrow(doc, x + 270, y + 105, x + 330, y + 105, 'snapshot', { dx: 8, dy: -2, lw: 50 });
  arrow(doc, x + 330, y + 128, x + 270, y + 128, 'telemetry', { dx: 8, dy: 12, lw: 50 });
  arrow(doc, x + 395, y + 150, x + 395, y + 175); arrow(doc, x + 215, y + 200, x + 215, y + 230, 'proxy');
  arrow(doc, x + 140, y + 160, x + 110, y + 185, null, { dash: true }); arrow(doc, x + 290, y + 190, x + 330, y + 245, 'metrics, OTLP', { dash: true, dx: -10, dy: 6 });
};

diagrams.deployment = (doc, x, y, W) => {
  doc.save(); doc.roundedRect(x + 60, y + 10, W - 70, 250, 6).dash(3, { space: 3 }).stroke('#8a94a3').undash(); doc.restore();
  text(doc, x + 66, y + 14, 'Kubernetes namespace api-platform (PSA restricted, default-deny NetworkPolicy)', { size: 8, bold: true });
  box(doc, x, y + 120, 54, 40, 'Internet', GR);
  box(doc, x + 76, y + 110, 90, 60, 'Ingress\nTLS (cert-manager)\n/gw/ -> gateway\n/ -> frontend', O);
  box(doc, x + 190, y + 40, 120, 52, 'gateway x3-20\nHPA CPU 60%\nPDB maxUnavail 1', { fill: '#dfeefc', stroke: '#0b5cad' });
  box(doc, x + 190, y + 105, 120, 52, 'control-plane x2-6\nHPA CPU 70%\nPDB minAvail 1', { fill: '#dfeefc', stroke: '#0b5cad' });
  box(doc, x + 190, y + 170, 120, 40, 'frontend x2 (nginx)\nuid 101, RO FS', { fill: '#dfeefc', stroke: '#0b5cad' });
  box(doc, x + 190, y + 218, 120, 36, 'orders-service x2\n(sample backend)', G);
  box(doc, x + 350, y + 40, 100, 44, 'Redis\n(managed in prod)', P); box(doc, x + 350, y + 105, 100, 44, 'MySQL\n(managed in prod)', G);
  box(doc, x + 350, y + 175, 100, 44, 'ConfigMap\nSecret refs only', GR);
  arrow(doc, x + 54, y + 140, x + 76, y + 140); arrow(doc, x + 166, y + 125, x + 190, y + 70); arrow(doc, x + 166, y + 150, x + 190, y + 188);
  arrow(doc, x + 310, y + 66, x + 350, y + 62); arrow(doc, x + 310, y + 131, x + 350, y + 127); arrow(doc, x + 250, y + 92, x + 250, y + 105, 'snapshot');
  arrow(doc, x + 250, y + 210, x + 250, y + 218, null); text(doc, x + 340, y + 232, 'Only gateway -> orders-service allowed', { size: 7 });
};

diagrams.er = (doc, x, y, W) => {
  const e = (cx, cy, w, title, fields, fill) => { box(doc, cx, cy, w, 14 + fields.length * 10, '', { fill: fill || '#ffffff', stroke: '#0b5cad' }); text(doc, cx + 4, cy + 3, title, { bold: true, size: 8, w: w - 8 }); fields.forEach((f, i) => text(doc, cx + 4, cy + 15 + i * 10, f, { size: 7, w: w - 8 })); };
  e(x, y, 120, 'api_definition', ['id PK', 'slug UK', 'owner_team', 'lifecycle', 'version (lock)']);
  e(x + 150, y, 120, 'api_version', ['id PK', 'api_id FK', 'label', 'base_path UK', 'status']);
  e(x + 300, y, 120, 'backend_target', ['id PK', 'api_version_id FK', 'url', 'weight 1..100', 'health_path']);
  e(x, y + 100, 120, 'policy_version', ['id PK', 'api_id FK', 'version_no (UK w/ api)', 'status', 'config_json', 'submitted_by / approved_by']);
  e(x + 150, y + 105, 120, 'subscription', ['id PK', 'consumer_id FK', 'api_id FK  (UK pair)', 'status']);
  e(x + 300, y + 105, 120, 'consumer', ['id PK', 'client_id UK', 'name UK', 'active']);
  e(x + 300, y + 190, 120, 'api_key', ['id PK', 'consumer_id FK', 'key_prefix UK', 'key_hash (sha256)', 'expires_at, revoked']);
  e(x, y + 215, 135, 'audit_event (append-only)', ['id, ts, actor, action', 'entity_type, entity_id', 'detail, correlation_id']);
  e(x + 150, y + 205, 135, 'traffic_bucket', ['batch_id + route + minute UK', 'requests, 4xx, 5xx', 'rate_limited, auth, timeouts', 'latency_sum, latency_max']);
  e(x + 150, y + 265, 135, 'operational_event', ['type, severity, route', 'message, correlation_id', '(+ idempotency_record)']);
  arrow(doc, x + 120, y + 25, x + 150, y + 25, '1..n', { lw: 30 }); arrow(doc, x + 270, y + 25, x + 300, y + 25, '1..n', { lw: 30 });
  arrow(doc, x + 60, y + 60, x + 60, y + 100, '1..n', { lw: 30 }); arrow(doc, x + 300, y + 130, x + 270, y + 130, 'n..1', { lw: 30 }); arrow(doc, x + 360, y + 160, x + 360, y + 190, '1..n', { lw: 30 });
  arrow(doc, x + 120, y + 45, x + 150, y + 125, null, { dash: true });
};

diagrams.lifecycle = (doc, x, y, W) => {
  const steps = [['1 Correlation id', 'validate or generate'], ['2 Route match', '404 route_not_found'], ['3 Method allowed?', '405 + Allow'], ['4 IP allowlist', '403'], ['5 Path safety', '400 invalid_path'], ['6 Authenticate / authorize', '401 / 403'], ['7 Rate limit (route + consumer)', '429 / 503 limiter down'], ['8 Request size', '413'], ['9 Bulkhead', '503 route_saturated'], ['10 Proxy: target, breaker, timeout, retry (safe only)', '502 / 503 / 504'], ['11 Respond + metrics + minute buckets', '']];
  steps.forEach((s, i) => { const yy = y + i * 30; box(doc, x + 10, yy, 250, 24, s[0], i === 9 ? { fill: '#dfeefc', bold: true } : {}); if (s[1]) { box(doc, x + 310, yy + 2, 150, 20, s[1], i < 9 ? O : GR); arrow(doc, x + 260, yy + 12, x + 310, yy + 12, 'reject', { lw: 40, dx: 10, dy: -3 }); } if (i < steps.length - 1) arrow(doc, x + 135, yy + 24, x + 135, yy + 30); });
};

diagrams.auth = (doc, x, y, W) => {
  text(doc, x, y, 'Operator (UI / REST / GraphQL)', { bold: true }); text(doc, x, y + 140, 'Consumer (data plane)', { bold: true });
  box(doc, x, y + 16, 80, 26, 'Browser', GR); box(doc, x + 110, y + 16, 90, 26, 'OIDC / dev IdP', P); box(doc, x + 230, y + 16, 120, 26, 'Control plane\nresource server', { fill: '#dfeefc' }); box(doc, x + 370, y + 16, 100, 26, 'Service layer\nAccessGuard (object)', G);
  arrow(doc, x + 80, y + 29, x + 110, y + 29, '1 login'); arrow(doc, x + 110, y + 40, x + 80, y + 46, '2 JWT', { dy: 8 }); arrow(doc, x + 80, y + 36, x + 230, y + 66, '3 Bearer JWT', { dx: 50, dy: 12 });
  arrow(doc, x + 350, y + 29, x + 370, y + 29, '4'); text(doc, x, y + 76, 'JWT verified: signature, issuer, exp -> roles/team claims -> @PreAuthorize (role) -> AccessGuard (owner team) -> audit', { size: 7.6, w: W });
  text(doc, x, y + 92, 'Policy approval: submitter != approver (self_approval -> 403); gateway channel uses X-Gateway-Token (ROLE_GATEWAY only on /internal/**).', { size: 7.6, w: W });
  box(doc, x, y + 158, 90, 26, 'API client', GR); box(doc, x + 120, y + 158, 130, 26, 'Gateway\nAuthenticator', { fill: '#dfeefc' }); box(doc, x + 280, y + 158, 120, 26, 'Snapshot\n(key hashes, subs)', G); box(doc, x + 140, y + 215, 110, 26, 'Backend\nX-Consumer-Id', GR);
  arrow(doc, x + 90, y + 171, x + 120, y + 171, 'X-API-Key / JWT'); arrow(doc, x + 250, y + 171, x + 280, y + 171, 'hash lookup'); arrow(doc, x + 185, y + 184, x + 185, y + 215, 'creds stripped, identity asserted', { dx: 6, lw: 160 });
  text(doc, x + 300, y + 200, 'prefix.secret -> SHA-256 constant-time compare', { size: 7.2, w: 200 }); text(doc, x + 300, y + 212, '-> expiry -> subscription to THIS api', { size: 7.2, w: 200 });
};

diagrams.propagation = (doc, x, y, W) => {
  const lanes = [['Approver', 20], ['Control plane', 140], ['MySQL', 260], ['Gateway', 370]];
  lanes.forEach(([n, lx]) => { box(doc, x + lx, y, 90, 20, n, { fill: '#dfeefc', bold: true }); doc.save(); doc.moveTo(x + lx + 45, y + 20).lineTo(x + lx + 45, y + 196).dash(2, { space: 3 }).stroke('#9aa5b4').undash(); doc.restore(); });
  const m = (a, b, yy, t, o) => arrow(doc, x + lanes[a][1] + 45, y + yy, x + lanes[b][1] + 45, y + yy, t, Object.assign({ lw: 150 }, o));
  m(0, 1, 40, 'approve policy', { dx: 6 }); m(1, 2, 62, 'tx: ACTIVE + audit', { dx: 6 }); m(2, 1, 84, 'commit', { dx: 6 });
  text(doc, x + 150, y + 96, 'AFTER_COMMIT: invalidate snapshot cache', { size: 7.2, w: 220 });
  m(3, 1, 130, 'poll If-None-Match (every 5 s)', { dx: -150, dy: 0 }); m(1, 3, 152, '200 + new snapshot (ETag)', { dx: -170 });
  text(doc, x + 340, y + 166, 'validate -> build -> atomic swap', { size: 7.2, w: 180 }); text(doc, x + 340, y + 178, '-> persist last-known-good', { size: 7.2, w: 180 });
  text(doc, x + 20, y + 205, 'Propagation delay = poll interval (+ <= 30 s cache staleness on other CP replicas).  Measured locally: ~7-8 s with a 2 s poll.', { size: 7.6, w: W - 20, bold: true });
  text(doc, x + 20, y + 220, 'Control plane down: gateways keep the last-known-good config; revocations are delayed until recovery.', { size: 7.6, w: W - 20 });
};

diagrams.ratelimit = (doc, x, y, W) => {
  box(doc, x, y + 10, 100, 30, 'Request', GR); box(doc, x + 130, y + 10, 130, 30, 'ResilientRateLimiter', { fill: '#dfeefc', bold: true });
  box(doc, x + 300, y, 160, 28, 'Redis Lua script (atomic)\nfixed | sliding | token bucket', P); box(doc, x + 300, y + 55, 160, 30, 'Local limiter\n(limit / EXPECTED_REPLICAS)', O);
  box(doc, x + 130, y + 90, 130, 30, 'Redis failed? back-off 5 s', GR);
  arrow(doc, x + 100, y + 25, x + 130, y + 25); arrow(doc, x + 260, y + 18, x + 300, y + 14, 'healthy'); arrow(doc, x + 260, y + 32, x + 300, y + 66, 'LOCAL mode', { dx: 5 }); arrow(doc, x + 195, y + 40, x + 195, y + 90);
  box(doc, x + 300, y + 105, 160, 30, 'CLOSED mode -> 503\nrate_limiter_unavailable', O);
  arrow(doc, x + 260, y + 110, x + 300, y + 120);
  text(doc, x, y + 150, 'Decision -> 200 + X-RateLimit-* | 429 + Retry-After.   Redis health is NOT part of readiness (degrade, do not drop out of service).', { size: 7.8, w: W });
  text(doc, x, y + 165, 'Fixed window: boundary burst up to 2x.  Sliding counter: weighted previous window.  Token bucket: steady rate + bounded burst.', { size: 7.8, w: W });
};

diagrams.observability = (doc, x, y, W) => {
  box(doc, x, y + 20, 100, 40, 'Gateway\nControl plane\nBackends', { fill: '#dfeefc' });
  box(doc, x + 150, y, 120, 28, 'Prometheus\n(scrape /actuator/prometheus)', P); box(doc, x + 150, y + 45, 120, 28, 'OTLP/HTTP -> Jaeger\n(traces, sampled)', P); box(doc, x + 150, y + 90, 120, 28, 'Structured logs\ntraceId + correlation id', P);
  box(doc, x + 310, y, 100, 28, 'Grafana\ndashboards, alerts', G); box(doc, x + 310, y + 45, 100, 28, 'Jaeger UI', G); box(doc, x + 310, y + 90, 100, 28, 'Log store (any)', G);
  box(doc, x, y + 100, 100, 34, 'Minute buckets +\nevents -> control plane', O); box(doc, x + 150, y + 140, 120, 28, 'Deterministic incident\nrules (evidence)', O); box(doc, x + 310, y + 140, 100, 28, 'Console: incidents,\nevents, audit', G);
  arrow(doc, x + 100, y + 30, x + 150, y + 14); arrow(doc, x + 100, y + 40, x + 150, y + 59); arrow(doc, x + 100, y + 50, x + 150, y + 104); arrow(doc, x + 270, y + 14, x + 310, y + 14); arrow(doc, x + 270, y + 59, x + 310, y + 59); arrow(doc, x + 270, y + 104, x + 310, y + 104);
  arrow(doc, x + 50, y + 60, x + 50, y + 100); arrow(doc, x + 100, y + 125, x + 150, y + 150); arrow(doc, x + 270, y + 154, x + 310, y + 154);
  text(doc, x, y + 178, 'Labels: route = apiSlug/version (bounded) or "unmatched"; never path, consumer, IP, status code. Failures of any backend never block requests.', { size: 7.8, w: W });
};

const chain = (labels, h) => (doc, x, y, W) => {
  const n = labels.length; const gap = 8; const bw = (W - gap * (n - 1)) / n;
  labels.forEach((l, i) => { box(doc, x + i * (bw + gap), y + 10, bw, h - 20, l, i % 2 ? { fill: '#eaf1fb' } : { fill: '#dfeefc', bold: true, size: 7.6 }); if (i < n - 1) arrow(doc, x + i * (bw + gap) + bw, y + h / 2, x + (i + 1) * (bw + gap), y + h / 2); });
};
diagrams.cicd = chain(['1 Compile', '2 Unit\ntests', '3 Integration\ntests', '4 Angular\nlint + test', '5 Angular\nbuild', '6 Static\nanalysis'], 70);
diagrams.cicd2 = chain(['7 Dependency\nscan', '8 Image\nbuild', '9 Image\nscan', '10 Helm/K8s\nvalidate', '11 Reports', '12 Versioned\nartifacts'], 70);
diagrams.helmflow = chain(['CI: build\n+ scan images', 'helm lint\n+ template\n+ kubeconform', 'package\nchart 1.0.0', 'helm upgrade\n--atomic', 'readiness\ngates rollout', 'smoke test\n/ promote'], 80);
diagrams.specflow = chain(['PRD', 'FR/NFR', 'Design\n(ADR)', 'Tasks', 'Implement', 'Tests', 'Security\nreview', 'Perf\nreview', 'Docs', 'Release\n(human)'], 70);

diagrams.multiagent = (doc, x, y, W) => {
  const R = (cx, cy, t, o) => box(doc, x + cx, y + cy, 105, 32, t, o || {});
  R(0, 0, 'R1 Requirements\n/ spec', { fill: '#fff1e0', stroke: '#d9822b' }); R(135, 0, 'R2 Architecture\n(ADR drafts)', { fill: '#fff1e0', stroke: '#d9822b' });
  R(0, 70, 'R3 Java backend'); R(135, 70, 'R4 Angular'); R(270, 70, 'R5 Test engineering', G);
  R(0, 140, 'R6 Security review', P); R(135, 140, 'R7 Perf + observability', P); R(270, 140, 'R8 Docs + release', GR);
  box(doc, x + 385, y + 0, 95, 32, 'HUMAN gates:\nspec, design', { fill: '#fde8e8', stroke: '#c0392b', bold: true }); box(doc, x + 385, y + 140, 95, 32, 'HUMAN gates:\nsecurity, release', { fill: '#fde8e8', stroke: '#c0392b', bold: true });
  arrow(doc, x + 105, y + 16, x + 135, y + 16); arrow(doc, x + 240, y + 16, x + 385, y + 16, null, { dash: true }); arrow(doc, x + 187, y + 32, x + 52, y + 70); arrow(doc, x + 187, y + 32, x + 187, y + 70); arrow(doc, x + 187, y + 32, x + 322, y + 70);
  arrow(doc, x + 52, y + 102, x + 52, y + 140); arrow(doc, x + 187, y + 102, x + 187, y + 140); arrow(doc, x + 322, y + 102, x + 322, y + 140); arrow(doc, x + 105, y + 156, x + 135, y + 156); arrow(doc, x + 240, y + 156, x + 270, y + 156); arrow(doc, x + 375, y + 156, x + 385, y + 156);
  text(doc, x, y + 185, 'Shared contracts (docs/api-contracts.md, ADRs, architecture-rules.md) are frozen inputs; file ownership per role; independent review for risky changes.', { size: 7.8, w: W });
  text(doc, x, y + 198, 'No multi-agent run was executed for this repository - this is the designed protocol with sample tasks/handoffs.', { size: 7.8, w: W, bold: true });
};

diagrams.context = (doc, x, y, W) => {
  box(doc, x, y + 10, 120, 40, 'project-context.md\n(always, ~60 lines)', { fill: '#dfeefc', bold: true }); box(doc, x, y + 70, 120, 30, 'repo-map.md\n(find files)', G);
  const mods = ['architecture-rules', 'java-spring-review', 'api-security-review', 'database-migration', 'angular-review', 'kubernetes-review'];
  mods.forEach((m, i) => { box(doc, x + 190 + (i % 2) * 150, y + 6 + Math.floor(i / 2) * 36, 140, 28, m + '.md', GR); });
  arrow(doc, x + 120, y + 30, x + 190, y + 20, 'task type selects'); box(doc, x + 190, y + 120, 290, 28, 'Task prompt = role + project-context + 1-2 modules + input artifact paths', O);
  text(doc, x, y + 160, 'Hand off summaries + paths (not transcripts); measure tokens from the tool usage report before claiming savings.', { size: 7.8, w: W });
};

diagrams.perfflow = (doc, x, y, W) => {
  box(doc, x, y + 10, 110, 30, 'Symptom\n(SLO burn / alert)', O); box(doc, x + 140, y + 10, 120, 30, 'Recent change?\n(audit, deploy)', GR); box(doc, x + 290, y + 10, 100, 30, 'Revert / rollback', G);
  box(doc, x + 140, y + 70, 120, 30, 'Split: gateway vs\nbackend (trace)', { fill: '#dfeefc' }); box(doc, x, y + 70, 110, 30, 'p50/p95/p99 by route\n(histogram_quantile)', P);
  const c = [['CPU?', 'profile / scale'], ['Heap/GC?', 'heap dump / allocation'], ['Pool pending?', 'slow query, pool size'], ['Threads/pinning?', 'thread dump, JFR'], ['Cardinality?', 'drop label, sample']];
  c.forEach((e, i) => { box(doc, x + i * 96, y + 135, 90, 22, e[0], { size: 7.6 }); box(doc, x + i * 96, y + 163, 90, 24, e[1], { fill: '#eaf6ee', stroke: '#2f8f5b', size: 7.2 }); arrow(doc, x + i * 96 + 45, y + 157, x + i * 96 + 45, y + 163); });
  arrow(doc, x + 110, y + 25, x + 140, y + 25); arrow(doc, x + 260, y + 25, x + 290, y + 25, 'yes'); arrow(doc, x + 200, y + 40, x + 200, y + 70, 'no'); arrow(doc, x + 140, y + 85, x + 110, y + 85); arrow(doc, x + 200, y + 100, x + 200, y + 135);
  text(doc, x + 290, y + 78, 'Measure before/after; record method + caveats', { size: 7.4, w: 190 });
};

diagrams.mindmap = (doc, x, y, W, C) => require('./mindmap')(doc, x, y, W, C, { box, text });

module.exports = diagrams;
