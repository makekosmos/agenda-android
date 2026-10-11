# Agent notes

## Product rule

UI talks to the in-process Engine (`engine-mobile`) through UniFFI, never to
SQLite or the OS directly. See README.md.

## Release

Releases are built and signed by CI (`.github/workflows/release.yml`). No local
keystore or signing env is needed.

1. Bump `versionName` and `versionCode` defaults in `app/build.gradle.kts`
   (one commit, e.g. `release: 0.1.8 (versionCode 9)`), push it to `main`.
2. Tag that commit `v<versionName>` and push the tag:
   `git tag v0.1.8 && git push origin v0.1.8`.
3. The workflow fails if the tag and `versionName` differ or if the APK is not
   signed with the release key (SHA-256 `617d4881…d2cb`). It publishes the
   APK, `manifest.json`, `latest-android.yml` and `release-bom-android.json`
   to the GitHub release; the unstripped `libagenda_mobile.so` (crash symbols)
   is a workflow artifact, not a release asset.
4. If the tag push does not start a run, start it by hand:
   `gh workflow run release.yml -f tag=v0.1.8`.
5. Check: `curl -sL https://github.com/makekosmos/agenda-android/releases/latest/download/manifest.json`
   shows the new version, and its `sha256` matches the APK.

Signing secrets live in the repo: `AGENDA_RELEASE_KEYSTORE_BASE64`,
`AGENDA_RELEASE_STORE_PASSWORD`, `AGENDA_RELEASE_KEY_ALIAS`,
`AGENDA_RELEASE_KEY_PASSWORD`. Never create a new key: installed apps only
accept updates signed with the same certificate.

## Local dev on macOS

`scripts/device.sh` boots the arm64 AVD `agenda35` with snapshots
(`COLD=1`, `WINDOW=1`). Debug/e2e APKs use the `android-dev` Rust profile;
only `*Release` tasks use fat LTO.
