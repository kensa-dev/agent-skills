# Author-skill eval: inventory-reservation (Kage acceptance)

This eval checks whether the `kensa-development` skill can AUTHOR an idiomatic Kage
acceptance test from a Given/When/Then brief.

## Prerequisite (important)

The eval runs a *real* Kage acceptance test, which only exists on the **kage branch**
of `kensa-dev/kensa` (the `:kage-acceptance` module). Before running:

1. Check out the kage branch somewhere locally (a git worktree is fine).
2. Set `input_paths.kage_acceptance_checkout` in `evals.json` to that path. Keep this
   local — do not commit an absolute machine path.

## What the eval does

1. Prompt = `inventory-reservation/brief.md` (a disposable GWT brief).
2. The skill (or baseline) generates a scenario + in-process runner into the
   checkout's `kage/acceptance/src/test/kotlin/dev/kensa/example/kage/` sources.
3. Three programmatic assertions grade the result (see `scripts/`):
   - `assert_structure.sh <scenario.kt> <runner.kt>` — Fixtures, MatcherField,
     `@ExpandableSentence`, `thenEventually`, `SequenceDiagramCapture`; no whole-body
     JSON/XML comparison.
   - `assert_compiles_and_passes.sh <checkout>` — `./gradlew :kage-acceptance:test
     --tests '*InventoryReservation*'` is green.
   - `assert_reviewer_clean.sh <scenario.kt>` — the kensa-development reviewer reports
     zero violations (headless `claude -p`; non-deterministic — treat as a graded
     signal, not a hard gate).
4. `golden/` holds the hand-written reference (the ideal output) for comparison.

## Running

Follow the skill-creator eval flow: spawn a with-skill run and a baseline run for
eval-0, save outputs per run, then grade each expectation (run the three scripts;
use `agents/grader.md` for the qualitative ones).
