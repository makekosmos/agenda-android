export ANDROID_HOME=/home/box/android-sdk
export ANDROID_SDK_ROOT=/home/box/android-sdk
export ANDROID_NDK_HOME=/home/box/android-sdk/ndk/27.2.12479018
export PATH=/home/box/.local/bin:/home/box/.cargo/bin:$ANDROID_HOME/platform-tools:$PATH
export CARGO_BUILD_JOBS=4
export GRADLE_OPTS="-Xmx1536m -Dorg.gradle.daemon=false"
# Desktop GPUI builds on this box need the fontconfig sysroot: run `agenda_desktop_env` first.
agenda_desktop_env() { export PATH=/home/box/kos-sysroot/bin:$PATH PKG_CONFIG_PATH=/home/box/kos-sysroot/usr/lib/x86_64-linux-gnu/pkgconfig PKG_CONFIG_SYSROOT_DIR=/home/box/kos-sysroot; }
