# ADR-0011: Ship a second, equivalent console in React

* Status: accepted - Date: 2026-10-17

## Context
The original brief mandated Angular only. A later explicit request asked for the same console in React, e.g. for interview preparation across both ecosystems (the job description mentions React or similar). The backend contract is UI-agnostic.

## Decision
Keep `frontend/` (Angular 20) as the primary console and add `frontend-react/` (React 18, Vite, TypeScript) with the **same pages, API usage, security posture and end-to-end suite**. They are independent builds; neither imports the other. The Playwright spec was reused unchanged, which is the executable proof of functional parity at the UI level.

## Alternatives
Replace Angular with React (rejected: breaks the brief); generate React from the Angular app with a tool (no reliable tool; would hide design differences); a shared UI core (Web Components) - more complexity than the benefit for a reference project.

## Consequences
+ Demonstrates framework-independent API design; contract regressions are caught by two consumers; practice material for both stacks.
- Two UIs to maintain (feature changes must land twice); different test pyramids (Karma/Jasmine vs Vitest); no shared component library; the React console uses plain CSS rather than Material.

## Review triggers
Feature drift between the consoles; a decision to standardise on one framework (delete the other); a need for a shared design system.
