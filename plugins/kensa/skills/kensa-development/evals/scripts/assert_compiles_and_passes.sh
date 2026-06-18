#!/usr/bin/env bash
# Usage: assert_compiles_and_passes.sh <kage-acceptance-checkout-dir>
# The generated files must already be placed in that checkout's test sources.
set -euo pipefail
proj="$1"
( cd "$proj" && ./gradlew :kage-acceptance:test --tests '*InventoryReservation*' --console=plain )
echo "COMPILE+RUN OK"
