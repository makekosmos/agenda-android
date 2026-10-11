import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi.plugin)
}

/** Optional local checkouts used via rust/.cargo/config.toml [patch]. */
fun localRustInputs(): List<File> = buildList {
    System.getenv("AGENDA_GPUI_DIR")?.let { add(File(it, "crates/agenda-core/src")) }
    System.getenv("AGENDA_CORTEX_DIR")?.let {
        add(File(it, "runtime/crates/engine-mobile/src"))
        add(File(it, "core/crates/ark-core/src"))
    }
}.filter { it.isDirectory }

val jniLibsDir = layout.buildDirectory.dir("rust-jnilibs")

val buildRustAndroid = tasks.register<Exec>("buildRustAndroid") {
    workingDir = rootDir
    commandLine("${rootDir}/scripts/build-rust.sh", "android")
    // -PagendaAbis=arm64-v8a narrows the Rust build (see build-rust.sh).
    (findProperty("agendaAbis") as String?)?.let { environment("AGENDA_ANDROID_ABIS", it) }
    inputs.property("agendaAbis", findProperty("agendaAbis") ?: "default")
    inputs.dir(rootDir.resolve("rust/src"))
    inputs.files(rootDir.resolve("rust/Cargo.toml"), rootDir.resolve("rust/Cargo.lock"))
    // Rust deps are git deps pinned in Cargo.toml/Cargo.lock. With a local
    // [patch] override, set AGENDA_CORTEX_DIR / AGENDA_GPUI_DIR so edits there
    // re-trigger this task.
    localRustInputs().forEach { inputs.dir(it) }
    outputs.dir(jniLibsDir)
}

// Release signing comes from ~/.gradle/gradle.properties or env vars
// (AGENDA_RELEASE_STORE_FILE / _STORE_PASSWORD / _KEY_ALIAS / _KEY_PASSWORD).
// Nothing secret lives in the repo; without them the release build is unsigned.
fun releaseSecret(name: String): String? =
    (findProperty(name) as String?) ?: System.getenv(name)

android {
    namespace = "com.mundus.agenda"
    compileSdk = 35

    signingConfigs {
        val storeFile = releaseSecret("AGENDA_RELEASE_STORE_FILE")
        if (storeFile != null) {
            create("release") {
                this.storeFile = file(storeFile)
                storePassword = releaseSecret("AGENDA_RELEASE_STORE_PASSWORD")
                keyAlias = releaseSecret("AGENDA_RELEASE_KEY_ALIAS")
                keyPassword = releaseSecret("AGENDA_RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.findByName("release")
            ndk {
                // Real phones: arm64 only. -PagendaAbis=x86_64 gives an
                // emulator build with the same R8 config (docs/testing-on-device.md).
                abiFilters += ((findProperty("agendaAbis") as String?) ?: "arm64-v8a")
                    .split(' ', ',').filter { it.isNotBlank() }
            }
        }
        // Release-equivalent build that keeps the 10.0.2.2 cleartext
        // exception (src/e2e overlay) so the self-update e2e can use a
        // local http.server feed. Production release stays clean.
        create("e2e") {
            initWith(getByName("release"))
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    defaultConfig {
        applicationId = "com.mundus.agenda"
        minSdk = 26
        targetSdk = 35
        // -PagendaVersionCode / -PagendaVersionName override for scratch
        // builds (e.g. an older "installed" build in the updater e2e).
        versionCode = ((findProperty("agendaVersionCode") as String?) ?: "4").toInt()
        versionName = (findProperty("agendaVersionName") as String?) ?: "0.1.3"
        // Self-update feed BASE (manifest.json first, latest-android.yml
        // fallback — cortex feed.rs:15,63-93). Override with
        // -PagendaUpdateFeedBase=… for tests.
        buildConfigField(
            "String",
            "UPDATE_FEED_BASE",
            "\"${(findProperty("agendaUpdateFeedBase") as String?) ?: "https://github.com/makekosmos/agenda-android/releases/latest/download"}\"",
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets {
        named("main") {
            jniLibs.srcDir(jniLibsDir)
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

tasks.named("preBuild") {
    dependsOn(buildRustAndroid)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Android needs JNA's AAR (it carries libjnidispatch.so per ABI); the
    // plain jar :core uses on the JVM only has desktop natives.
    implementation(project(":core")) {
        exclude(group = "net.java.dev.jna", module = "jna")
    }
    implementation(libs.jna) { artifact { type = "aar" } }
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.compose.material.icons.extended)
    testImplementation(libs.junit)
    testImplementation(libs.jna)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
