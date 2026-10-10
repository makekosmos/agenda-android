#!/usr/bin/env bash
# make-update-feed.sh <apk> <base-url> [out-dir] [--merge <manifest.json>]
#
# Emits the Android self-update feed for a built APK, mirroring the Cortex
# release contract (desktop/scripts/release-manifest.mjs):
#   manifest.json            — primary feed; additive platforms.android
#                              entry (schema_version 1 allows extra keys,
#                              release-manifest.mjs:34-35). With --merge,
#                              merges platforms.android into an existing
#                              manifest like mergeReleaseManifest does
#                              for platforms.mac (release-manifest.mjs,
#                              mergeReleaseManifest: remote fields win).
#   latest-android.yml       — legacy electron-builder-style fallback
#                              (legacyChannelYml), sha256 + versionCode.
#   release-bom-android.json — tooling-only BOM (release-bom.mjs shape).
set -euo pipefail

APK=""
BASE_URL=""
OUT_DIR="."
MERGE=""

while [ $# -gt 0 ]; do
    case "$1" in
        --merge) MERGE="$2"; shift 2 ;;
        *) if [ -z "$APK" ]; then APK="$1"
           elif [ -z "$BASE_URL" ]; then BASE_URL="$1"
           else OUT_DIR="$1"; fi
           shift ;;
    esac
done

if [ -z "$APK" ] || [ -z "$BASE_URL" ]; then
    echo "usage: $0 <apk> <base-url> [out-dir] [--merge <manifest.json>]" >&2
    exit 1
fi

AAPT2="$(ls -d "${ANDROID_HOME:-/home/box/android-sdk}"/build-tools/*/aapt2 | tail -1)"
BADGING="$("$AAPT2" dump badging "$APK" | sed -n '/^package:/p;q' || true)"
VERSION_NAME="$(echo "$BADGING" | sed -n "s/.*versionName='\([^']*\)'.*/\1/p")"
VERSION_CODE="$(echo "$BADGING" | sed -n "s/.*versionCode='\([^']*\)'.*/\1/p")"
FILE="$(basename "$APK")"
SIZE="$(stat -c%s "$APK")"
SHA256="$(sha256sum "$APK" | cut -d' ' -f1)"
SHA512="$(openssl dgst -sha512 -binary "$APK" | base64 -w0)"
COMMIT="$(git -C "$(dirname "$0")/.." rev-parse HEAD)"
RELEASED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"

mkdir -p "$OUT_DIR"

python3 - "$OUT_DIR" "$MERGE" <<PYEOF
import json, sys

out_dir, merge_path = sys.argv[1], sys.argv[2]

entry = {
    "file": "$FILE",
    "url": "${BASE_URL%/}/$FILE",
    "size": $SIZE,
    "sha256": "$SHA256",
    "sha512": "$SHA512",
    "versionCode": $VERSION_CODE,
    "target": "aarch64-linux-android",
    "commit": "$COMMIT",
    "released_at": "$RELEASED_AT",
}

if merge_path:
    with open(merge_path) as f:
        manifest = json.load(f)
    if manifest.get("version") != "$VERSION_NAME":
        # mergeReleaseManifest refuses to mix versions across platforms.
        sys.exit("merge manifest version %s != %s" % (manifest.get("version"), "$VERSION_NAME"))
else:
    manifest = {
        "schema": "mundus-release-manifest",
        "schema_version": 1,
        "product": "mundus",
        "version": "$VERSION_NAME",
        "channel": "production",
        "source": {
            "repository": "makekosmos/agenda-android",
            "commit": "$COMMIT",
            "toolchain": {},
        },
        "compatibility": {"engine_api": "0.1.0"},
        "platforms": {},
    }

manifest["version"] = manifest.get("version") or "$VERSION_NAME"
manifest.setdefault("platforms", {})["android"] = entry
with open(out_dir + "/manifest.json", "w") as f:
    json.dump(manifest, f, indent=2, sort_keys=True)
    f.write("\n")

bom = {
    "schema_version": 2,
    "id": "agenda-android-$VERSION_NAME-android",
    "release": {"version": "$VERSION_NAME", "channel": "production", "platform": "android"},
    "source": {
        "repository": "makekosmos/agenda-android",
        "commit": "$COMMIT",
        "toolchain": {"target": "aarch64-linux-android"},
    },
    "compatibility": manifest.get("compatibility", {}),
    "artifacts": [{"file": "$FILE", "sha256": "$SHA256", "size": $SIZE, "versionCode": $VERSION_CODE}],
}
with open(out_dir + "/release-bom-android.json", "w") as f:
    json.dump(bom, f, indent=2, sort_keys=True)
    f.write("\n")
PYEOF

cat > "$OUT_DIR/latest-android.yml" <<EOF
version: $VERSION_NAME
versionCode: $VERSION_CODE
files:
  - url: $FILE
    sha256: $SHA256
    size: $SIZE
path: $FILE
sha256: $SHA256
releaseDate: '$RELEASED_AT'
EOF

echo "wrote $OUT_DIR/{manifest.json,latest-android.yml,release-bom-android.json} ($FILE v$VERSION_NAME code $VERSION_CODE)"
