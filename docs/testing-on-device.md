# Testing on a real Android runtime

How an agent (Devin CLI) boots an Android device on the box, installs the app,
drives it and catches crashes without a human. Roborazzi screenshots
(`./gradlew :app:recordRoborazziDebug`) stay the fast layout check; this is
the "does it actually run" check.

## Device

- Emulator + system images: `/home/box/android` (emulator 37.2.12).
  `adb`: `/home/box/android-sdk/platform-tools/adb`.
- AVDs (default `AVD=agenda34atd` recommended): `agenda34atd` (`system-images;android-34;aosp_atd;x86_64`, lightest,
  preferred) and `agenda35` (API 35 google_apis x86_64).
- The emulator needs KVM to be usable. The box user is not in the group that
  owns `/dev/kvm` (root:103, 0660), so `scripts/device.sh boot` falls back to
  `-accel off` (pure software, TCG). See "Status" below for what that means.

```
AVD=agenda34atd scripts/device.sh boot      # headless, waits for sys.boot_completed
scripts/device.sh stop                      # always stop it when done (2+ GB RAM)
```

## Build for the emulator

Release has the same R8 config as the phone build; only the ABI differs.

```
scripts/device.sh build release   # = ./gradlew :app:assembleRelease -PagendaAbis=x86_64
scripts/device.sh install         # app/build/outputs/apk/release/app-release.apk
```

`-PagendaAbis` narrows both the Rust `.so` build and the release `abiFilters`
(default `arm64-v8a` for phones).

## Drive it: mobile-mcp (preferred)

[mobile-mcp](https://github.com/mobile-next/mobile-mcp) 1.0.9 is installed at
`/home/box/tools/mobile-mcp` and registered for Devin CLI at user scope
(`devin mcp add mobile-mcp --scope user ...`), file
`~/.config/devin/mcp_config.json`:

```json
{
  "mcpServers": {
    "mobile-mcp": {
      "command": "/usr/bin/node",
      "args": ["/home/box/tools/mobile-mcp/node_modules/@mobilenext/mobile-mcp/lib/index.js"],
      "env": {
        "ANDROID_HOME": "/home/box/android-sdk",
        "PATH": "/home/box/android-sdk/platform-tools:/usr/local/bin:/usr/bin:/bin"
      },
      "transport": "stdio"
    }
  }
}
```

It gives screenshots, the accessibility tree with coordinates, tap/swipe/type,
launch/install. Check with `devin mcp list`. The device must be booted first.

## Drive it: plain adb (fallback)

`scripts/device.sh` (run with no args for help):

| command | does |
|---|---|
| `launch` / `kill` / `clear` | start, force-stop, wipe data |
| `shot out.png` | screenshot |
| `tap X Y`, `swipe X1 Y1 X2 Y2 [ms]`, `text "..."`, `key KEYCODE_ENTER` | input |
| `back` | edge-swipe back gesture (tests predictive back animation) |
| `backkey` | KEYCODE_BACK |
| `ui [out.xml]` | uiautomator dump |
| `find "label"` / `tapon "label"` | locate / tap a node by text or content-desc |
| `logcat [out.txt]` | full logcat dump |
| `crash` | prints FATAL EXCEPTION / native crash, exits 1 if any |

Typical smoke loop: `clear; launch; shot 0.png; tapon "<label>"; shot 1.png; crash`.

## Status (2026-10-10, measured on the box, no KVM, TCG)

| step | agenda34atd (ATD API 34) | agenda35 (google_apis API 35) |
|---|---|---|
| boot to `sys.boot_completed` | 390 s | never: system_server died and zygote restarted 11x in 13 min |
| `adb install` release APK (30 MB) | 81 s | - |
| cold start (`am start -W`) | 12.4 s (+ a "system isn't responding" ANR dialog right after boot, tap Wait) | - |
| warm start | 4.8 s | - |
| `uiautomator dump` | ~27 s | - |
| `screencap` | ~23 s, and the image is **black** (rendering broken under TCG + swiftshader_indirect) | - |

So without KVM the rig works for **crash/logcat and UI-tree driven tests**
(tap by coordinates from `ui`/`find`, then `crash`), but not for visual checks:
screenshots come back blank. Untested next step: boot with `-gpu guest`.

First result: tapping "+" (content-desc "Новая задача") on the x86_64 release
build reproduces Jack's crash:

```
FATAL EXCEPTION: main  Process: com.mundus.agenda
SerializationException: Serializer for class 'QuickEntryPreview' is not found.
  at com.mundus.agenda.EngineAgendaRepository$quickEntryPreview$2.invokeSuspend
```

`QuickEntryPreview` (core/.../BoardRepository.kt) has no `@Serializable`, but
`EngineAgendaRepository.quickEntryPreview` decodes it with
`json.decodeFromString`. It is not R8: debug builds crash the same way.

Options that give a usable device:

1. KVM for the box user (infra change: add the box user to group 103 or an
   ACL on `/dev/kvm`). Then `device.sh boot` uses `-accel on` automatically.
2. GitHub Actions emulator (`reactivecircus/android-emulator-runner`) on a
   public repo (Actions minutes are free there; private repos in makekosmos
   have no working Actions billing).
