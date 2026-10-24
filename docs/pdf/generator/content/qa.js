'use strict';
const groups = [...require('./qa1'), ...require('./qa2'), ...require('./qa3'), ...require('./qa4')];

let n = 0;
let scenarios = 0;
const body = [];
for (const g of groups) {
  body.push({ h2: g.cat });
  for (const [s, q, a, f, t] of g.items) {
    n += 1;
    if (s) scenarios += 1;
    body.push({ h3: `Q${n}${s ? ' [Scenario]' : ''}. ${q}` });
    body.push({ p: a });
    body.push({ p: `**Likely follow-ups:** ${f}` });
    body.push({ p: `**In this project:** ${t}` });
  }
}

const summary = groups.map((g) => [g.cat, String(g.items.length), String(g.items.filter((i) => i[0]).length)]);
summary.push(['**Total**', `**${n}**`, `**${scenarios}**`]);

module.exports = [
  { h1: 'Part 2 - Interview questions with model answers' },
  { p: `This part contains **${n} questions** grouped by topic; **${scenarios} are scenario-based** (marked [Scenario]). Each model answer gives the technical reasoning, trade-offs and failure modes; "Likely follow-ups" lists what a good interviewer asks next; "In this project" ties the answer to code, tests or documents you can demonstrate.` },
  { table: { head: ['Topic', 'Questions', 'Scenario-based'], widths: [4, 1, 1.2], rows: summary } },
  { note: 'How to practise: cover the answer, speak yours for 2-3 minutes (reasoning, trade-off, failure mode, example), then compare. Prepare one concrete story per topic from this project - and be honest about what was not verified (see the validation report).' },
  ...body,
  { h1: 'Appendix - quick reference' },
  { table: { head: ['Topic', 'One-line answer to remember'], widths: [2.4, 6], rows: [
    ['Gateway design', 'Immutable route table + atomic config swap; control plane off the hot path; bounded everything.'],
    ['Rate limits', 'Token bucket for APIs; atomic Lua in Redis; decide LOCAL vs CLOSED when Redis is down.'],
    ['Policy propagation', 'Versioned snapshots, ETag polling, validate-then-swap, last-known-good; bounded, not instantaneous.'],
    ['504 investigation', 'Split by code and target, compare timeout vs backend p99, check retries and breakers, revert recent changes first.'],
    ['SSRF', 'Allowlist + blocked ranges + no redirects + re-check at connect + egress policy.'],
    ['Auth for many consumers', 'Per-consumer identity, hashed expiring keys or short-lived JWTs, subscription scoping, never forward credentials.'],
    ['Cardinality', 'Labels only from bounded registries; detail goes to logs/traces.'],
    ['JVM memory', 'Heap after GC, class histogram, heap dump; bound structures before raising limits.'],
    ['Spec-driven AI', 'Spec -> design -> small tasks -> real verification -> independent review -> human approval.'],
    ['Agent conflicts', 'Frozen contracts, file ownership, gates, evidence, escalation to architecture then human.'],
    ['Context optimisation', 'Short always-on file + task modules + repo map + handoffs; measure tokens and pass rate.'],
    ['Dependency patching', 'Evidence, reachability, smallest bump, regression tests first, canary, rollback; accept with owner/expiry if no fix.'],
    ['Perf miss at release', 'Quantify, profile, fix or roll back, decide with owner; never ship on hope.'] ] } },
];
