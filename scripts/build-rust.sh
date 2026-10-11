#!/usr/bin/env bash
# Build the Rust core and generate Kotlin bindings.
#   scripts/build-rust.sh android   # .so per ABI into app/build/rust-jnilibs
#   scripts/build-rust.sh host      # host .so for the :core JVM test
#   scripts/build-rust.sh bindings  # Kotlin bindings into core/build/generated/uniffi
#   scripts/build-rust.sh all       # all of the above
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RUST_DIR="$ROOT/rust"
OUT_JNILIBS="$ROOT/app/build/rust-jnilibs"
OUT_BINDINGS="$ROOT/core/build/generated/uniffi"

# shellcheck disable=SC1091
[ -f "$ROOT/.devin-env.sh" ] && source "$ROOT/.devin-env.sh"

MODE="${1:-all}"

build_host() {
    (cd "$RUST_DIR" && cargo build --lib)
}

build_android() {
    # AGENDA_ANDROID_ABIS="arm64-v8a" limits the build (release APK for
    # real phones); default keeps x86_64 for emulators.
    local abis="${AGENDA_ANDROID_ABIS:-arm64-v8a x86_64}" targets=() abi
    for abi in $abis; do targets+=(-t "$abi"); done
    # Drop stale ABI dirs so a narrowed build doesn't ship old .so files.
    rm -rf "$OUT_JNILIBS"
    # Bundled sqlite: drop modules the schema never uses (size audit p10 —
    # FTS3/4 ~157 KB, RTREE ~31 KB). libsqlite3-sys hard-codes the -D
    # enables; a later -U on the same command line wins. FTS5 and JSON1
    # stay — the ark-core schema uses both.
    export LIBSQLITE3_FLAGS="-USQLITE_ENABLE_FTS3 -USQLITE_ENABLE_FTS3_PARENTHESIS -USQLITE_ENABLE_RTREE"
    (cd "$RUST_DIR" && cargo ndk "${targets[@]}" -o "$OUT_JNILIBS" build --release)
    # cargo-ndk copies every cdylib in the dep tree; the app ships exactly
    # one .so (agenda_mobile statically contains engine-mobile + ark-core).
    find "$OUT_JNILIBS" -name '*.so' ! -name 'libagenda_mobile.so' -delete
}

build_bindings() {
    build_host
    mkdir -p "$OUT_BINDINGS"
    (cd "$RUST_DIR" && cargo run --features cli --bin uniffi-bindgen -- \
        generate --library "$RUST_DIR/target/debug/libagenda_mobile.so" \
        --language kotlin --out-dir "$OUT_BINDINGS")
}

case "$MODE" in
    android) build_android ;;
    host) build_host ;;
    bindings) build_bindings ;;
    all) build_android && build_bindings ;;
    *) echo "usage: $0 [android|host|bindings|all]" >&2; exit 2 ;;
esac
