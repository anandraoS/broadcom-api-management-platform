'use strict';
const path = require('path');
const { build } = require('./lib/engine');

const out = path.resolve(__dirname, '..');
const which = process.argv[2] || 'all';

async function main() {
  if (which === 'all' || which === 'arch') {
    const blocks = [...require('./content/arch1'), ...require('./content/arch2')];
    const n = await build({
      title: 'Enterprise API Gateway, Security Policy and Observability Control Plane',
      subtitle: 'Architecture and End-to-End Engineering Guide',
      coverLines: [
        { text: 'Broadcom_API_Management_Observability_Architecture', bold: true, size: 12, color: '#0b5cad' },
        { text: 'A reference implementation of API management and observability: Java 21 / Spring Boot control plane and gateway, Angular console, MySQL, Redis, Docker, Kubernetes, Helm, CI/CD, and a specification-driven, AI-assisted engineering workspace.' },
        { text: 'Scope and honesty: all data is synthetic and all credentials are dummy local-only values. This guide separates what was executed and verified (Java and Angular builds and tests, live gateway routing, static analysis, npm audit, end-to-end browser tests) from what was only authored and reviewed (Docker images, Kubernetes manifests, Helm chart, CI workflow, observability stack, Redis Lua scripts, MySQL migration test). See docs/validation/validation-report.md for the exact list.', size: 10 },
        { text: 'Companion files: docs/architecture.md, docs/security.md, docs/adr/, docs/traceability-matrix.md, and the Deep Dive and Interview Guide PDF.', size: 10, color: '#5f6b7a' },
      ],
    }, blocks, path.join(out, 'Broadcom_API_Management_Observability_Architecture.pdf'));
    console.log('architecture PDF written, toc entries:', n);
  }
  if (which === 'all' || which === 'guide') {
    const blocks = [...require('./content/guide1'), ...require('./content/guide2'), ...require('./content/qa')];
    const n = await build({
      title: 'Senior Engineer Deep Dive and Interview Guide',
      subtitle: 'Java, API management, distributed systems, Kubernetes, security, observability and AI-assisted engineering',
      coverLines: [
        { text: 'Broadcom_Senior_Engineer_Deep_Dive_and_Interview_Guide', bold: true, size: 12, color: '#0b5cad' },
        { text: 'A study reference tied to a working project: every topic links to code, tests or clearly labelled conceptual material in the accompanying repository. Part 1 is the technical reference; Part 2 contains 120+ interview questions with model answers (40+ scenario-based), trade-offs, failure modes and likely follow-ups.' },
        { text: 'How to use it: read the topic chapters, then answer each question aloud before reading the model answer. Where an answer cites project evidence (a test name, file or measured number) you can open it in the repository and demonstrate it.', size: 10 },
        { text: 'Honesty note: measured numbers come from a single shared-CPU laptop and are relative only; items not executed in the build environment are labelled as such.', size: 10, color: '#5f6b7a' },
      ],
    }, blocks, path.join(out, 'Broadcom_Senior_Engineer_Deep_Dive_and_Interview_Guide.pdf'));
    console.log('guide PDF written, toc entries:', n);
  }
}
main().catch((e) => { console.error(e); process.exit(1); });
