#!/usr/bin/env bash
# Regenerates core/src/test/resources/parity-golden.json from desktop code:
# `seed-parity` lives in agenda-core (makekosmos/agenda-unified, $AGENDA_GPUI_DIR) and
# prints the seed dataset as engine objects plus agenda-core's expected
# smart-list/project/tag contents for a fixed LocalDay (Monday UTC+3).
set -euo pipefail
cd "$(dirname "$0")/.."
AGENDA="${AGENDA_GPUI_DIR:?set AGENDA_GPUI_DIR to an agenda-gpui checkout (branch main)}"
OUT=core/src/test/resources/parity-golden.json
cargo run --manifest-path "$AGENDA/Cargo.toml" -p agenda-core --bin seed-parity -- 2026-10-12 10800 > "$OUT"
echo "wrote $OUT"
