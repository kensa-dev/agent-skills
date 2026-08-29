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

4. **Self-review** — the main context dispatches a second subagent to critique the emitted test against the brief and the inventory. The subagent returns a list of issues; the main context applies the fixes and re-dispatches until the reviewer reports zero violations (`self-review.md`).

---

## Hard gate

**Introspect and generate start only when intake reports the brief complete.** A vague or
incomplete request stays in intake, one question at a time (`intake.md`). A test built on
incomplete requirements will not reflect the intended behaviour.

---

## Scope

Only the **Kage acceptance** shape is supported: Kage is Kensa's acceptance-test harness
(https://kensa.dev/kage), which gives a project its http stubs, Replay scenarios and a
`:kage-acceptance` module. Introspect gates on that infrastructure (`introspect.md`, Kage
acceptance gate).

---

## With the kensa MCP server

When the server is registered, introspect starts from `style_profile` (`introspect.md`, MCP head
start) and authoring ends by confirming the new class green (`../mcp-tools.md`, Authoring).
