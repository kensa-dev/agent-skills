#!/usr/bin/env bash
# Usage: assert_reviewer_clean.sh <generated-scenario.kt>
# Runs the kensa-development reviewer headlessly; passes iff zero violations.
set -euo pipefail
scen="$1"
out=$(claude -p "Use the kensa-development skill to REVIEW this Kensa test. \
List only concrete best-practice violations, one per line, prefixed 'VIOLATION:'. \
If none, output exactly 'CLEAN'.\n\n$(cat "$scen")" --model sonnet)
echo "$out"
echo "$out" | grep -q "^CLEAN$" && { echo "REVIEWER CLEAN"; exit 0; }
echo "$out" | grep -q "VIOLATION:" && { echo "REVIEWER FOUND VIOLATIONS" >&2; exit 1; }
echo "REVIEWER inconclusive" >&2; exit 1
