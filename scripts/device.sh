#!/usr/bin/env bash
# Drive the Agenda app on an Android emulator/device via plain adb.
# Fallback for agents when the mobile-mcp MCP server is unavailable.
# See docs/testing-on-device.md.
#
#   scripts/device.sh boot                 # start headless emulator (AVD $AVD, default agenda30 = only image with working screenshots here), wait for boot
#     env: AVD=agenda34atd (API 34, no screenshots), GPU=guest|swiftshader_indirect|host, EMU_EXTRA="...", KVM=1 (never on this box)
#   scripts/device.sh stop                 # kill the emulator
#   scripts/device.sh build [release|debug]  # x86_64 APK for the emulator
#   scripts/device.sh install [apk]        # default: latest x86_64 release build
#   scripts/device.sh launch | kill | clear
#   scripts/device.sh shot out.png         # screenshot to file
#   scripts/device.sh tap X Y | swipe X1 Y1 X2 Y2 [ms] | text "hello" | key KEYCODE
#   scripts/device.sh back                 # predictive back via edge swipe
#   scripts/device.sh backkey              # KEYCODE_BACK
#   scripts/device.sh ui [out.xml]         # uiautomator dump (prints to stdout if no file)
#   scripts/device.sh find "label"         # center x y of first node whose text/content-desc contains label
#   scripts/device.sh tapon "label"        # find + tap
#   scripts/device.sh logcat [out.txt]     # dump logcat (app pid + crashes)
#   scripts/device.sh crash                # print last FATAL EXCEPTION / native crash, exit 1 if found
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SDK="${ANDROID_SDK_ROOT_EMU:-/home/box/android}"          # has emulator + system image
ADB="${ADB:-/home/box/android-sdk/platform-tools/adb}"
AVD="${AVD:-agenda30}"
SERIAL="${ANDROID_SERIAL:-emulator-5554}"
PKG="com.mundus.agenda"
EMU_LOG="${EMU_LOG:-/tmp/agenda-emulator.log}"
a() { "$ADB" -s "$SERIAL" "$@"; }

accel_flag() {
    # Default: pure software (TCG). On this box /dev/kvm exists but creating a vCPU
    # hits a host kernel BUG (kvm_spurious_fault, nested VMX broken), so KVM is opt-in
    # only (KVM=1) for machines where it really works.
    if [ "${KVM:-0}" = 1 ]; then echo "-accel on"; else echo "-accel off"; fi
}

cmd="${1:-}"; shift || true
case "$cmd" in
  boot)
    if a get-state >/dev/null 2>&1; then echo "already running: $SERIAL"; exit 0; fi
    start=$(date +%s)
    # shellcheck disable=SC2046
    ANDROID_SDK_ROOT="$SDK" ANDROID_HOME="$SDK" nohup "$SDK/emulator/emulator" -avd "$AVD" \
        $(accel_flag) -no-window -no-audio -no-boot-anim -gpu "${GPU:-guest}" \
        ${EMU_EXTRA:-} -no-snapshot -memory 2048 -cores 4 -port "${SERIAL#emulator-}" >"$EMU_LOG" 2>&1 &
    "$ADB" start-server >/dev/null
    until [ "$(a shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do
        sleep 5
        if [ $(( $(date +%s) - start )) -gt "${BOOT_TIMEOUT:-1800}" ]; then echo "boot timeout, see $EMU_LOG"; exit 1; fi
    done
    # Lighter UI: no animations would hide animation bugs, so keep them unless NOANIM=1.
    if [ "${NOANIM:-0}" = 1 ]; then
        for k in window_animation_scale transition_animation_scale animator_duration_scale; do a shell settings put global $k 0; done
    fi
    a shell input keyevent KEYCODE_WAKEUP; a shell wm dismiss-keyguard || true
    a shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null || true  # post-boot "isn't responding" dialogs
    echo "booted in $(( $(date +%s) - start ))s ($(accel_flag))"
    ;;
  stop) a emu kill || true ;;
  build)
    variant="${1:-release}"
    (cd "$ROOT" && source .devin-env.sh && ./gradlew ":app:assemble${variant^}" -PagendaAbis=x86_64 -q)
    ls -la "$ROOT/app/build/outputs/apk/$variant/"*.apk
    ;;
  install)
    apk="${1:-$ROOT/app/build/outputs/apk/release/app-release.apk}"
    a install -r -t "$apk"
    ;;
  launch) a shell monkey -p "$PKG" -c android.intent.category.LAUNCHER 1 >/dev/null; sleep "${WAIT:-3}" ;;
  kill) a shell am force-stop "$PKG" ;;
  clear) a shell pm clear "$PKG" ;;
  shot) out="${1:?out.png}"; a exec-out screencap -p >"$out"; echo "$out" ;;
  tap) a shell input tap "$1" "$2" ;;
  swipe) a shell input swipe "$1" "$2" "$3" "$4" "${5:-300}" ;;
  text) a shell input text "$(printf '%s' "$1" | sed 's/ /%s/g')" ;;
  key) a shell input keyevent "$1" ;;
  back)
    read -r w h < <(a shell wm size | awk -F'[ x]' '/Physical/{print $3, $4}')
    a shell input swipe 2 $((h/2)) $((w/3)) $((h/2)) 250
    ;;
  backkey) a shell input keyevent KEYCODE_BACK ;;
  ui)
    a shell uiautomator dump /sdcard/ui.xml >/dev/null
    if [ -n "${1:-}" ]; then a pull /sdcard/ui.xml "$1" >/dev/null; echo "$1"; else a exec-out cat /sdcard/ui.xml; fi
    ;;
  find|tapon)
    label="${1:?label}"
    xy=$("$0" ui | python3 -c '
import sys,re,xml.etree.ElementTree as ET
lab=sys.argv[1]
for n in ET.fromstring(sys.stdin.read()).iter("node"):
    if lab in (n.get("text","")+"|"+n.get("content-desc","")):
        x1,y1,x2,y2=map(int,re.findall(r"\d+",n.get("bounds")))
        print((x1+x2)//2,(y1+y2)//2); break' "$label")
    [ -n "$xy" ] || { echo "not found: $label" >&2; exit 1; }
    if [ "$cmd" = tapon ]; then a shell input tap $xy; fi
    echo "$xy"
    ;;
  logcat)
    if [ -n "${1:-}" ]; then a logcat -d -v threadtime >"$1"; echo "$1"; else a logcat -d -v threadtime; fi
    ;;
  crash)
    out=$(a logcat -d -b crash -v threadtime; a logcat -d -v threadtime | grep -A40 -E "FATAL EXCEPTION|Fatal signal|\*\*\* \*\*\* \*\*\*" || true)
    if [ -n "$out" ]; then echo "$out"; exit 1; else echo "no crash"; fi
    ;;
  *) sed -n '2,22p' "$0"; exit 2 ;;
esac
