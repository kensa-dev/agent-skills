# Self-Review Phase

This document describes the self-review phase that runs after generation. Read `overview.md` first.

---

## Purpose

The generator holds the brief, the inventory, and all design decisions it just made. That context makes it easy to rationalize choices that violate best practices. A fresh subagent has no generation context — it can only judge the test against the rules as written.

---

## Step 1 — Dispatch a fresh reviewer subagent

Dispatch a subagent with NO generation context. Give it:

- The generated test file(s) — full text, not a summary.
- The complete brief (acceptance criteria, actor names, field names).
- The inventory snapshot returned by introspect (available infrastructure, fixture names, interaction paths).
- The best-practice rules from `SKILL.md` (copy the "The Best Practices" section verbatim, or pass the file path and instruct the subagent to read it).
- `references/dsl.md`, which carries the timeout and negative-assertion rules the best practices point at.
- The other on-demand references relevant to what appears in the test (fixtures, interactions, rendered-value, setup-steps, captured-outputs — whichever apply).

The subagent receives exactly the items above and forms its own judgment.

Instruct the subagent to:

1. Read the test in full.
2. Identify every rendered context (test method bodies, `@ExpandableSentence` bodies).
3. Apply each best practice (BP-1 through BP-7) and the authoring checks below.
4. Report concrete violations only — file and approximate line, which rule, what specifically is wrong. If there are no violations, say "zero violations."

---

## Step 2 — Fix loop

When the subagent reports violations:

1. Apply every fix directly to the generated files in the main context.
2. Re-dispatch the reviewer subagent on the updated files, again with no generation context.
3. Repeat until the reviewer reports zero violations.

The main context applies the fixes; the reviewer judges.

When the reviewer reports zero violations, self-review is complete. Deliver the final files to the user.

---

## What to check especially

These are the flaws real reviews have caught in authored tests. The reviewer checks every one.

### Dead fixtures

A fixture is declared in the `FixtureContainer` (or registered via `registerFixtures`) but never flows through to an assertion. Every fixture must be traceable: defined → consumed in a given/action → visible in a `then` or `thenEventually` call, either as a `fixtures[key]` reference in a state collector or as a value in a matcher.

Flag any fixture that is declared but whose value is never asserted.

### Implementation-language names in rendered positions

Names in test method bodies and `@ExpandableSentence` bodies appear verbatim in HTML reports read by non-developers, so they are domain language.

Flag any of:

- "captured" — e.g. `theCapturedReservationResponse` → prefer `theReservationResponse`
- "holder" — e.g. `theHolderResult` → prefer `theResult`
- "stub" / "mock" — e.g. `theStubResponse` → prefer the domain name
- "actual" / "expected" as bare prefixes — e.g. `theActualStatus`
- Any other implementation/test-infra noun where a domain noun would serve

The test: a BA reading the report sentence recognises every word as domain language.

### Whole-body JSON or XML comparison

Assertions that compare entire JSON or XML payloads as strings or raw structures are fragile, unreadable, and produce poor report sentences. Every significant response field must be asserted individually via a named matcher or a `MatcherField` / `thatHas(field of value, ...)` construct.

Flag any `shouldBe(rawJson)`, `assertEquals(xmlString, ...)`, or equivalent whole-body comparison. Flag also any inline validation DSL with more than two `shouldBe` shapes repeated — these are missing flat matchers (BP-7).

### Fixtures actually flow and are asserted

Check that fixture values are not merely set up in a `given` and then silently dropped. Trace each fixture key from definition through to where the system under test uses it (the action) and then to what the test asserts about the outcome. A fixture that drives an input but whose effect is never verified is a coverage gap.

### Test body and @ExpandableSentence bodies read as prose

Read every rendered context aloud as a sentence a BA would write in a specification. Flag:

- Variable assignments in the body (`val x = ...`)
- Loops or conditionals
- Raw lambda blocks inline in the body
- Qualifier prefixes that break fluency (`steps.`, `holder.`, `context.` — unless the qualifier is itself a named actor in the domain, e.g. `orchestration.sends(...)`)
- `@ExpandableSentence` on an Action-returning function (BP-2)

---

## Reviewer output format

The reviewer subagent must return exactly one of:

```
zero violations
```

or a numbered list:

```
1. [BP-x / authoring check name] <file>:<approx line> — <what is wrong, concise>
2. ...
```

Violations only.
