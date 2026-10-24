# AI code-review and quality-control examples

## Why generated code is untrusted until reviewed and validated
Assistants produce *plausible* code, not *verified* code. They (1) optimise for the pattern they have seen most, which includes insecure and outdated patterns; (2) can invent APIs, properties or versions that compile in their head only; (3) rarely know your invariants (four-eyes, allowlists, bounded state) unless told; (4) are confidently wrong about concurrency, security edge cases and performance; (5) write tests that mirror the implementation and therefore pass when both are wrong; (6) cannot see what they did not read. Treat every generated line like a pull request from an unknown contributor: read it, run it, attack it, and make the tests fail on purpose (mutation) to prove they bite. The review checklists (`docs/governance/code-review-checklist.md`, the module files in `ai-engineering/`) exist to turn that stance into a routine.

## Coverage map (checklist item -> example)

| Review item | Example |
|---|---|
| Incorrect business logic | 7 (check-then-act version number) and 2 |
| Architectural boundary violation | 8 (leaky layering) |
| Insecure authorization | 2 |
| SQL injection | 1 |
| Secrets in source/logs | 6 |
| Race conditions | 7 |
| Inefficient DB queries | 9 |
| Unbounded retries | 3 |
| Resource leaks / missing timeouts | 4 and 5 |
| Unsafe configuration changes | 10 |
| Inadequate tests | 11 |
| Performance bottlenecks | 9 |
| Overengineering | 12 |

All examples are in `examples.md`; each lists the flawed (AI-style) code or design, the risk, the corrected version **as implemented in this repository** (with file paths), and the regression test that guards it (names refer to real tests). Examples marked *(design)* describe a flawed design rather than code.
