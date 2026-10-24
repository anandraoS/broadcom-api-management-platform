# Context engineering and token optimization guide

Goal: give an assistant exactly the context it needs, no more - so answers are grounded, cheaper and faster. **No token-saving percentage is claimed here;** measure with the method in section 9.

## 1. Build a concise project context
Keep one always-loaded file (`project-context.md`, ~60-80 lines): purpose, module boundaries, commands, hard constraints, invariants, Definition of Done, inspect-before-change rules. Everything else is a pointer. Rule of thumb: if a fact is derivable from one `grep` or one file read, store the *path*, not the content.

## 2. Retrieve selectively
Order of cheapest-first retrieval: (1) `repo-map.md` to locate files; (2) `grep`/symbol search for the exact identifier; (3) read the **smallest relevant range** (function, not file); (4) read related tests (they show intended behaviour); (5) only then broaden. Never paste whole directories; never paste generated files, lockfiles, `target/`, `node_modules/`, PDFs.

## 3. Repository maps
`repo-map.md` lists packages and one-line responsibilities and the 3-5 files that matter per area. Keep it under 60 lines and regenerate when structure changes (a stale map is worse than none).

## 4. Summarise completed work
After each task write a *handoff note* (template in `multi-agent/handoffs/_template.md`): goal, paths changed, decisions and why, evidence, open questions. When a conversation is long, start a fresh session with: project context + the handoff note + the next task - not the transcript.

## 5. Preserve architectural decisions
Decisions live in ADRs (`docs/adr/`) and `architecture-rules.md`, each with *review triggers*. Agents load the rule file, not the discussion. A decision an agent "remembers" from earlier chat is not a decision.

## 6. Avoid redundant context
Don't restate rules the assistant can read from files; don't re-send unchanged large files each turn (reference by path); remove stale instructions; one source of truth per fact (link instead of copy). Keep instruction files free of examples that duplicate the codebase.

## 7. Split large tasks
Task = one behaviour, <= ~1 day, explicit inputs/outputs and files (see `multi-agent/tasks/`). Large asks ("build the gateway") become a dependency-ordered list; each subtask runs with a small, task-specific context bundle.

## 8. Load specialised instructions on demand
Modules in `ai-engineering/*.md` are loaded per task type (table in `README.md`). A Java bug fix needs `project-context` + `java-spring-review`; it does not need Kubernetes or Angular rules.

## 9. Detect missing context
Signals: the assistant invents a class/property/endpoint; asks you to confirm things stated in the repo; produces code that violates a stated invariant; hedges ("probably"); cites no file paths. Response: ask it to list the files it read and what it assumed, then supply the missing file or rule - do not just re-prompt louder. Add a check to the prompt: "List unknowns as questions before coding."

## 10. Reduce repeated explanations
Put recurring corrections into the instruction modules ("never forward inbound credentials") so they are written once; keep a short "lessons" section in the relevant module after each review finding.

## Before / after example
`context-examples/context-before.md` (monolithic, duplicated, stale) vs `context-examples/context-after.md` (modular index + pointers) - same project facts. Trade-offs: the "after" form needs maintenance discipline, pushes some knowledge behind file reads (more tool calls), and can hide relevant rules if the index is wrong; the "before" form is simple but costs tokens on every turn and drifts out of date.

## Measuring token usage (method - run it, do not guess)
1. Pick 5 representative tasks (bug fix, new endpoint, review, migration, UI change).
2. For each task run the assistant with the *before* context and with the *after* context, same model, same prompt, fresh session.
3. Record from the tool's usage report (not estimates): input tokens, output tokens, number of tool calls, wall time, and whether the result passed your tests/review.
4. Compare medians per task type and the pass rate - a cheaper context that lowers correctness is a regression.
5. Optionally count static size: `wc -w` / a tokenizer on the always-loaded files (this measures the fixed overhead per turn only).
6. Re-measure after instruction changes; keep the results next to the files (e.g. `context-examples/measurements.md`) with dates and model versions.
