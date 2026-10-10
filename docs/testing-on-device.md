# Testing on a real Android runtime

How an agent (Devin CLI) boots an Android device on the box, installs the app,
drives it and catches crashes without a human. Roborazzi screenshots
(`./gradlew :app:recordRoborazziDebug`) stay the fast layout check; this is
the "does it actually run" check.

## Device

- Emulator + system images: `/home/box/android` (emulator 37.2.12).
  `adb`: `/home/box/android-sdk/platform-tools/adb`.
- AVDs:
  - `agenda30` (default): `system-images;android-30;default;x86_64`, Pixel 6
    skin 1080x2400. **The only image on this box whose screenshots are not
    black.** No system predictive-back (API 30); in-app back transitions still
    run (system back = KEYCODE_BACK / 3-button nav).
  - `agenda34atd`: `android-34;aosp_atd;x86_64`. Boots faster (~220 s) and
    has API 34 behaviour (predictive back callbacks), but nothing renders:
    `screencap` is black and `gfxinfo` shows 0 frames rendered. Use it only for
    crash/logcat/uiautomator checks of API 34 behaviour.
  - `agenda35` (API 35 google_apis): unusable (system_server restart loop).
- **No KVM on this box.** `/dev/kvm` exists but creating a vCPU hits a host
  kernel BUG (`kvm_spurious_fault` in `kvm_arch_vcpu_create`, dmesg; nested
  VMX is advertised but broken). Never boot with `KVM=1` here: every try
  oopses the box kernel. `device.sh boot` always uses `-accel off` (TCG).

```
scripts/device.sh boot                      # agenda30, headless, waits for sys.boot_completed
AVD=agenda34atd scripts/device.sh boot      # API 34, behaviour only (no screenshots)
scripts/device.sh stop                      # always stop it when done (~2.5 GB RAM)
```

Knobs: `GPU=` (default `guest`; the images fall back to host
swiftshader/lavapipe anyway), `EMU_EXTRA="..."` extra emulator flags.

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

## Status (2026-10-10 evening, measured on the box, TCG)

| step | agenda30 (API 30 default) | agenda34atd (ATD API 34) |
|---|---|---|
| boot to `sys.boot_completed` | ~400 s | ~220 s (390 s with the old flags) |
| `adb install` release APK (30 MB) | 13 s | 81 s |
| cold start (`am start -W` TotalTime) | 7.0 s | 3.3-10.8 s |
| `screencap` | ~15-20 s, **real image** | black (0 frames rendered) |
| mobile-mcp from Devin (list devices, screenshot, list elements) | works | elements only |

Right after boot a "System UI isn't responding" dialog may appear (slow
TCG); `device.sh boot` closes system dialogs, otherwise `tapon Wait`.

What did not fix the black screenshots on API 34 ATD: `-gpu swiftshader_indirect`,
`-gpu guest` (image has no guest renderer, falls back to swangle/lavapipe),
`-gpu host` on an own Xvfb (Mesa llvmpipe), `-feature -Vulkan`, and
`service call SurfaceFlinger 1008 i32 1` (force client composition, as root).
Switching to the API 30 image did.

mobile-mcp probe (Devin, swe-2-medium): `mobile_list_available_devices`,
`mobile_take_screenshot`, `mobile_list_elements_on_screen` all succeeded
against `agenda30` with the app in foreground.

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
