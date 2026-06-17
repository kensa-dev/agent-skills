# Eval result — iteration-2 (with author skill)

The `kensa-development` skill now has the AUTHOR capability (Plan 1b). This is the
with-skill run, contrasted with the iteration-1 RED baseline.

## Headline

Given only `brief.md` + its own read-only introspection of the project — **with no
access to the golden** — the skill generated a test **materially identical to the
hand-written golden**, and slightly cleaner (it dropped the redundant `@RenderedValue
holder` the golden carries). The only diffs are import ordering and whitespace.

## Assertions

| Assertion | Baseline (iter-1) | With skill (iter-2) |
|---|---|---|
| `assert_structure.sh` (Fixtures, MatcherField, `@ExpandableSentence`, `thenEventually`, `SequenceDiagramCapture`) | ❌ RED (4 of 5 idioms missing) | ✅ **STRUCTURE OK** |
| `assert_compiles_and_passes.sh` (`:kage-acceptance:test --tests '*InventoryReservation*'`) | ❌ (no compiling test) | ✅ **COMPILE+RUN OK** |
| `assert_reviewer_clean.sh` (headless generic reviewer) | n/a | ⚠️ 5 findings — all golden-equivalent (see below) |

The four idioms the baseline missed — Fixtures, MatcherField, `@ExpandableSentence`,
`thenEventually` — are all present and correct in the with-skill output.

## One skill-content fix during iteration

First generation compiled-failed on fixture imports (`dev.kensa.FixtureContainer` /
`import dev.kensa.fixture` package import). Fixed the **skill** (not the test): added
Rule 0 (canonical imports) to `generate-kage-acceptance.md`. Regeneration compiled
clean. This is the eval working as intended — a real gap surfaced and was closed in
the skill.

## Reviewer findings — analysis

The headless generic reviewer reported 5 findings. **All five apply equally to the
golden** (which passed a rigorous quality review in Plan 1a):

1. `scenario.` qualifier on DSL calls — golden uses it ×9; established in every
   `kage-acceptance` scenario.
2. two consecutive `given(` → `and(` — golden has two `given(` too.
3. raw MatcherField assertion in the `then` body should be a named semantic matcher —
   golden does the same; the `aQuantityField of … and …` flow is already semantic.
4. raw `shouldBe` in `thenEventually` → named matcher (`shouldBeConfirmed()`) — golden
   uses raw `shouldBe`.
5. infra via `@BeforeEach`/`@AfterEach` + `registerFixtures` in `init{}` rather than a
   JUnit extension — golden + every in-process test in the module do exactly this.

**Conclusion:** none is a skill regression — the skill matched the accepted reference.
Findings 1, 2, 5 are codebase-established patterns the generic reviewer shouldn't
enforce here. Findings 3, 4 are genuine general Kensa idioms (semantic-matcher
wrapping) that the *golden itself* doesn't follow — a refinement opportunity for the
golden + generate rules, not a skill defect.

## Status

**Deterministic eval: GREEN.** The author capability is proven end-to-end: brief →
introspect → generate → compiling, idiomatic, passing Kage acceptance test.

**Open (for decision):** the `reviewer-clean` hard gate is mis-calibrated for this
codebase (it suggests patterns the whole module doesn't use). Either downgrade it to
advisory, recalibrate its prompt to judge against the codebase's idioms, or adopt
semantic-matcher wrapping in both the golden and the generate rules (findings 3, 4).
