# Authoring Pipeline Overview

This document describes how the `kensa-development` skill authors a new Kensa test end-to-end. Read this before consulting any phase-specific reference.

---

## The four phases and where each runs

| Phase | Runs in | Reference |
|---|---|---|
| Intake & gate | main context (interactive) | `intake.md` |
| Introspect | a dispatched subagent | `introspect.md` |
| Generate | main context | `generate-kage-acceptance.md` |
| Self-review | a dispatched subagent | `self-review.md` |

---

## Order & data flow

Phases run strictly in sequence:

1. **Intake** — the main context converses with the user to establish what behaviour is being tested, which actors are involved, and what the acceptance criteria are. Intake ends when the brief is complete: every required field documented in `intake.md` has a concrete answer. The output is a *complete brief*.

2. **Introspect** — the main context dispatches a subagent to examine the target codebase and produce an *inventory*: the modules present, the test infrastructure available (http stubs, Kage plugins, existing fixtures, actor names, etc.). The subagent returns the inventory as structured text; the main context consumes it in the next phase.

3. **Generate** — the main context holds both the complete brief and the inventory and uses them together to emit the test. See `generate-kage-acceptance.md` for the Kage acceptance shape.

4. **Self-review** — the main context dispatches a second subagent to critique the emitted test against the brief and the inventory. The subagent returns a list of issues (if any). The main context applies fixes directly; it does not re-dispatch for a second review cycle unless the changes are substantial.

---

## Hard gate

**Never proceed to introspect or generate until intake reports the brief is complete.**

If the user's request is vague, ambiguous, or missing required fields, stay in intake. Ask targeted questions one at a time; do not front-load a list of questions. A test built on incomplete requirements will not reflect the intended behaviour — no amount of good implementation recovers a bad brief.

See `intake.md` for the completeness checklist.

---

## Scope (increment 1)

Only the **Kage acceptance** shape is supported in this increment.

Before proceeding past intake, the introspect subagent MUST confirm that the project has Kage acceptance infrastructure: a `:kage-acceptance`-style Gradle module (or equivalent), an http-stub plugin, and a Kage plugin wired into the test build. If that infrastructure is absent, report the gap clearly and stop — do not fabricate a Pattern-A toolbox test or any other shape as a substitute.

---

## Graceful degradation

Introspect is "ask for the inventory." Today that means dispatching a subagent which reads the codebase and returns structured text (`introspect.md` specifies what to collect and how to format the response). When a `kensa mcp` server exists it will answer the same inventory questions directly, removing the need for the subagent entirely. The generate phase consumes the inventory as structured text regardless of how it was produced — it does not need to know which path was used.
