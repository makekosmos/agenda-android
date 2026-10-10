# Agenda Android

Native Android port of Agenda (KOS-370): a Jetpack Compose + Material 3 app
on top of the in-process Mundus Engine, linked as one `.so` through UniFFI.

## Product rule

The app talks to the in-process Engine library (`engine-mobile`) through
UniFFI, never to SQLite or the OS directly. UI reads only the local Engine
database; refresh is event-driven via the change-subscription callback.

## Layout

- `rust/` — crate `agenda-mobile` (cdylib + lib). Re-exports engine-mobile's
  UniFFI surface (`MobileEngine`, `ChangeListener`, sync ops) and adds thin
  exports over `agenda-core`: `parseQuickEntry`, `smartListIndices`,
  `taskFromEngineObject`/`taskToEngineObject` (the `com.kosmos.task` object
  shape agenda-gpui writes). Path deps on the cortex and agenda-gpui
  worktrees until their PRs merge, then they become git deps pinned to SHAs.
- `core/` — JVM Kotlin module with the generated UniFFI bindings (under
  `build/generated/uniffi`, not committed) and `AgendaRepository`, the
  single boundary the app uses (tasks flow, quick-entry add).
- `app/` — Compose app (`minSdk 26`, `targetSdk 35`): one screen, quick-entry
  text field + add button, task list with stable keys. Data dir is
  `Context.filesDir`; no network on screen open.
- `scripts/build-rust.sh` — builds the Rust side and the Kotlin bindings.
- `docs/` — `program.md` (program brief), `feature-inventory.md` (desktop
  parity checklist), `decisions.tsv` (append-only decision trail).

## Build

Prereqs: JDK 17, Android SDK 35 + NDK 27 (`$ANDROID_HOME`,
`$ANDROID_NDK_HOME`), Rust 1.95 with `aarch64-linux-android` and
`x86_64-linux-android` targets, `cargo-ndk`. Copy `.devin-env.example.sh` to `.devin-env.sh` and source it for the
env this box uses.

```bash
./gradlew :core:test           # host JVM test against the Linux .so
./gradlew :app:assembleDebug   # APK at app/build/outputs/apk/debug/
cd rust && cargo test          # Rust-side roundtrip + parse tests
```

Gradle runs the Rust builds itself: `:core:test` builds the host
`libagenda_mobile.so` (`cargo build`) and generates the Kotlin bindings
(`uniffi-bindgen --library`), `:app:*` builds the Android `.so`s for
arm64-v8a and x86_64 via `cargo ndk` into `app/build/rust-jnilibs`.
`./scripts/build-rust.sh [android|host|bindings|all]` does the same steps
by hand.

## Phase 0 limits

No emulator is available on this box (no KVM), so the Android-side check is
the `:core` host test: it exercises the same generated Kotlin bindings —
create a task, list it, receive `object_upserted`, reopen and still see it —
against the host-built library. On-device verification is pending hardware.
Sync ops (`startSync`, pairing) are exported but not yet wired into the UI.
