#!/usr/bin/env bash
# sync-e2e.sh — run the phone<->desktop sync E2E test.
#
# Builds (cached) the desktop Engine from $AGENDA_CORTEX_DIR (cortex checkout),
# boots it with a fresh MUNDUS_DATA_DIR, then runs
# :core:test --tests com.mundus.agenda.SyncE2eTest with
# SYNC_E2E_DESKTOP_LOCK pointing at engine.lock.json.
# Everything is tee'd to build/sync-e2e.log.
set -u

cd "$(dirname "$0")/.."
ROOT=$(pwd)
mkdir -p "$ROOT/build"
LOG="$ROOT/build/sync-e2e.log"
: > "$LOG"

exec > >(tee -a "$LOG") 2>&1

set -x
[ -f "$ROOT/.devin-env.sh" ] && source "$ROOT/.devin-env.sh"
set +x

CORTEX="${AGENDA_CORTEX_DIR:?set AGENDA_CORTEX_DIR to a cortex checkout (branch kos-370-android)}"
BINARY="$CORTEX/target/debug/mundus-engine"

DESK=$(mktemp -d /tmp/sync-e2e-desk.XXXXXX)
ENGINE_STDOUT="$DESK/engine.stdout.log"
ENGINE_PID=""

cleanup() {
    set +e
    if [ -f "$DESK/engine.lock.json" ]; then
        lock_pid=$(grep -o '"pid"[ :]*[0-9]*' "$DESK/engine.lock.json" | grep -o '[0-9]*' | head -1)
        [ -n "${lock_pid:-}" ] && kill "$lock_pid" 2>/dev/null
    fi
    [ -n "${ENGINE_PID:-}" ] && kill "$ENGINE_PID" 2>/dev/null
    sleep 1
    [ -n "${ENGINE_PID:-}" ] && kill -9 "$ENGINE_PID" 2>/dev/null
    rm -rf "$DESK"
}
trap cleanup EXIT

echo "== building desktop engine (cached) =="
(cd "$CORTEX" && cargo build -p engine) || { echo "engine build failed"; exit 1; }

# Gradle's buildRustHost only tracks rust/src; the path deps (engine-mobile,
# ark-core, agenda-core) can change underneath it. Build the host lib here so
# the test always runs against current sources.
echo "== building host libagenda_mobile (cached) =="
(cd "$ROOT/rust" && cargo build --lib) || { echo "host lib build failed"; exit 1; }

echo "== starting desktop engine, data dir $DESK =="
MUNDUS_DATA_DIR="$DESK" MUNDUS_HEADLESS=1 "$BINARY" >"$ENGINE_STDOUT" 2>&1 &
ENGINE_PID=$!

for i in $(seq 1 60); do
    [ -f "$DESK/engine.lock.json" ] && break
    if ! kill -0 "$ENGINE_PID" 2>/dev/null; then
        echo "engine exited early; stdout:"
        cat "$ENGINE_STDOUT"
        exit 1
    fi
    sleep 0.5
done
if [ ! -f "$DESK/engine.lock.json" ]; then
    echo "timed out waiting for engine.lock.json; stdout:"
    cat "$ENGINE_STDOUT"
    exit 1
fi
cat "$DESK/engine.lock.json"

echo "== running SyncE2eTest =="
# cleanTest: the env var is not a Gradle input, so a green run would cache
# :core:test UP-TO-DATE and the next invocation would skip the test entirely.
SYNC_E2E_DESKTOP_LOCK="$DESK/engine.lock.json" \
    ./gradlew :core:cleanTest :core:test --tests com.mundus.agenda.SyncE2eTest
STATUS=$?

echo "== engine stdout tail =="
tail -n 50 "$ENGINE_STDOUT" || true
echo "== done, status=$STATUS =="
exit $STATUS
