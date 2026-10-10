# Agenda Android program brief

Owner: Jack Kaza (Kosmos). Epic: KOS-370 (its text says GPUI on Android; Jack decided on 2026-10-10 to go native Compose instead, see decisions.tsv).

## Decided scope (2026-10-10)

1. Feature parity of a native Android Agenda with the current desktop Agenda (`makekosmos/agenda-gpui`, worktree `$AGENDA_GPUI_DIR`, branch `kos-370-android`). Inventory real features from code first: smart lists (`SmartList` in the model), tasks, projects, tags, quick entry parsing, reminders, theme (`src/palettes.rs`), undo.
2. Minimal port of Cortex/Engine (`makekosmos/cortex`, worktree `$AGENDA_CORTEX_DIR`, branch `kos-370-android`) so the phone syncs with desktop Engine. Rust core via UniFFI + cargo-ndk for `aarch64-linux-android` (also `x86_64-linux-android`). No localhost HTTP or lockfile on the phone. Data dir comes from Kotlin (`Context.filesDir`). Desktop-only modules (dictation, tray, autostart, updater, Windows crates) stay out. Reuse the existing object operations and dispatcher (`runtime/crates/engine-base/src/engine_dispatch.rs`, ark-core) rather than inventing a new API. Add a change-subscription callback (Rust to Kotlin Flow).
3. Local-first sync like Todoist/Linear. UI reads only the local DB. Writes are optimistic. Outbound changes survive process death (persisted in SQLite). Conflicts use the existing `before` check (agenda-gpui `src/store.rs` save). Refresh is event-driven. WorkManager periodic sync + sync on open + manual sync.
4. UI is Jetpack Compose + Material 3 with Agenda colors. Linear-level polish, Todoist-level instant response (stable list keys, no network on screen open).

Out of scope now: voice input. Do not decompile Todoist or Linear APKs.

## Product rule

Apps call Engine/platform APIs and must not know the OS. On Android the app talks to the in-process Engine library (engine-mobile) through UniFFI, never to SQLite directly.

## Repos and placement

- `agenda-gpui`: extract `agenda-core` (pure logic, no GPUI) as a Cargo workspace member. Make `filter_idx` and `parse_quick_entry_capture` pure (`today` passed in).
- `cortex`: new crate `runtime/crates/engine-mobile` (UniFFI, no HTTP server, no lockfile).
- `agenda-android` (this repo): Kotlin/Gradle app, minSdk 26, plus `rust/` crate that links engine-mobile + agenda-core into one `.so` for UniFFI.
- Reminders are inexact (no `SCHEDULE_EXACT_ALARM`).

## Phases and exit gates

- Phase 0. Rust core builds for Android. Kotlin bindings generated. Minimal Compose screen creates a task and lists from local DB. A task created on the phone side reaches desktop Engine after sync and vice versa. The box has no usable KVM (`/dev/kvm` not accessible), so the sync gate runs as a host test: the same Kotlin bindings on the JVM against the host-built library, syncing with a real desktop Engine process on the box.
- Phase 1. Read parity: smart lists, task view, projects, tags, theme.
- Phase 2. Write parity: quick entry, complete, reschedule, edit, projects, tags, undo; optimistic; conflict tests.

## Rules

- Each unit ends in a real check (cargo test, gradle build/test, sync test). Paste the command and result into `docs/decisions.tsv`.
- Private repos use local gates only. Public repos (agenda-gpui, cortex) keep GitHub Actions; do not delete workflows.
- Never force-push shared branches. Never use `--no-verify`. Never delete data or repos. Do not touch `makekosmos/store`.
- Do not push or open PRs yourself; the coordinator does that.
- Box memory is tight (about 2.5 GB free). Use `CARGO_BUILD_JOBS=4`, no Gradle daemon, and build one thing at a time.
- Environment: `source .devin-env.sh (copy of .devin-env.example.sh)`.

## Decision trail

`docs/decisions.tsv`, append-only, one row per decision or verified unit (ts, phase, decision, why, evidence, result).
