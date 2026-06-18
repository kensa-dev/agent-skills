# Intake — Brief Format and No-Slop Gate

Intake is a conversation. Its job is to produce a *complete brief* — nothing more. The brief is disposable working material, never saved as a maintained spec (that would re-introduce the drift problem Kensa exists to avoid). Proceed to Introspect only when every required field is present.

---

## The GWT-native brief shape

Capture the brief in this form (fill every field before leaving intake):

```
title:         one-line behaviour statement
participants:  who/what + role  (SUT | stub/supplier | broker | client …)
given:         preconditions / fixtures / stub primings
when:          the interaction under test
then:
  - outcome: "…", timing: immediate     → then / and
  - outcome: "…", timing: eventually    → thenEventually / andEventually
  - outcome: "…", timing: continually   → thenContinually / andContinually
```

---

## Accept loose prose too

If the user gives prose, a ticket, or a vague description, **derive the brief shape from it**, then reflect it back to the user for confirmation. This keeps the user's language in the test while ensuring every required field is resolved before generation begins.

---

## No-slop gate — completeness checklist

The brief is *complete enough to author* only when **all** of the following are satisfied:

1. **Title** — a concrete one-line behaviour statement (not a placeholder or a question).
2. **SUT identified** — at least one named participant has role `SUT`.
3. **When** — at least one concrete interaction under test (not "something happens").
4. **Then with declared timing** — at least one outcome with an explicit timing (`immediate`, `eventually`, or `continually`).

If anything is missing, **interview the user**: ask the smallest set of questions to fill the gaps. Do **not** generate until the gate is satisfied. Never invent missing requirements.

---

## Timing is declared, not guessed

If a `then` outcome's timing is unstated and could plausibly be asynchronous, **ask**. Do not silently default to `immediate`. The timing choice maps directly to a different DSL call (`then` vs `thenEventually` vs `thenContinually`) — getting it wrong changes the test's semantics.

---

## Interview discipline

- Ask one topic at a time; do not front-load a list.
- When the user answers, reflect the updated brief back and confirm before continuing.
- Stop asking once the gate is satisfied — do not over-specify.
