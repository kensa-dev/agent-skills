# Baseline result — iteration-1 (RED)

Captured before the author capability exists (the `kensa-development` skill is still
review-only). A capable general agent was given `brief.md` and allowed to read the
existing `kage/acceptance` scenarios for reference, then asked to author the test.

## Outcome: RED

`assert_structure.sh` exits **1** — first failure: `no Fixtures usage`.

| Idiom | Baseline |
|---|---|
| Kensa `Fixtures` | ❌ missing (inline literals instead) |
| MatcherField (`JsonIntField`/`JsonTextField`) field-by-field | ❌ missing (inline `shouldContain`-style assertions) |
| `@ExpandableSentence` drill-down | ❌ missing (used a plain `then` block) |
| `thenEventually` for the async outcome | ❌ missing (hand-rolled busy-poll loop inside a `then` step) |
| `SequenceDiagramCapture` | ✅ present (copied from the reference) |

## Interpretation

Reading the example scenarios gets a baseline agent the *mechanical* wiring
(server/stub/subscribe/diagram) but **not the idioms that make a Kensa test
valuable**: fixtures (index/search), field-by-field MatcherField assertions,
expandable drill-down, and the `thenEventually` DSL. Those four gaps are what the
author skill (Plan 1b) must close to turn this eval GREEN.

The eval therefore discriminates: structure + reviewer assertions are not trivially
satisfied by a capable agent without the skill.
