#!/usr/bin/env bash
# Usage: assert_structure.sh <generated-scenario.kt> <generated-runner.kt>
# The idiomatic primitives are split across both files (Fixtures in the scenario;
# MatcherField/@ExpandableSentence/thenEventually/SequenceDiagramCapture in the
# runner), so every check runs over the COMBINED content of both files.
set -euo pipefail
scen="$1"; runner="$2"
both=$(cat "$scen" "$runner")
fail() { echo "STRUCTURE FAIL: $1" >&2; exit 1; }
chk()  { grep -Eq "$1" <<<"$both" || fail "$2"; }

chk "FixtureContainer|registerFixtures|fixtures\("  "no Fixtures usage"
chk "Json[A-Za-z]*Field|testsupport\.field"         "no MatcherField (JsonField) flow"
chk "@ExpandableSentence"                            "no @ExpandableSentence drill-down"
chk "thenEventually"                                 "async outcome not via thenEventually"
chk "SequenceDiagramCapture"                         "no SequenceDiagramCapture"
# anti-patterns: whole-body JSON/XML comparison
grep -Eq "XmlUnit|xmlunit|isSimilarTo|assertEquals\([^,]*\{" <<<"$both" \
  && fail "raw JSON/XML whole-body comparison present" || true
echo "STRUCTURE OK"
