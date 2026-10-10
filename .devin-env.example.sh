# Copy to .devin-env.sh (gitignored) and adjust paths for your machine.
# Scripts source .devin-env.sh when it exists.
export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
export ANDROID_SDK_ROOT=$HOME/android-sdk
export ANDROID_NDK_HOME=$HOME/android-sdk/ndk/27.2.12479018
export PATH=$HOME/.local/bin:$HOME/.cargo/bin:$ANDROID_HOME/platform-tools:$PATH
export CARGO_BUILD_JOBS=4
export GRADLE_OPTS="-Xmx1536m -Dorg.gradle.daemon=false"
# Desktop GPUI builds on this box need the fontconfig sysroot: run `agenda_desktop_env` first.
agenda_desktop_env() { export PATH=$HOME/kos-sysroot/bin:$PATH PKG_CONFIG_PATH=$HOME/kos-sysroot/usr/lib/x86_64-linux-gnu/pkgconfig PKG_CONFIG_SYSROOT_DIR=$HOME/kos-sysroot; }
export JAVA_HOME=$HOME/jdk17
export PATH=$JAVA_HOME/bin:$PATH
# Optional local checkouts (with rust/.cargo/config.toml [patch]):
# export AGENDA_CORTEX_DIR=$HOME/src/cortex
# export AGENDA_GPUI_DIR=$HOME/src/agenda-gpui
