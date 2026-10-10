import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

val rustDir = rootDir.resolve("rust")
val generatedDir = layout.buildDirectory.dir("generated/uniffi")

val buildRustHost = tasks.register<Exec>("buildRustHost") {
    workingDir = rustDir
    commandLine("cargo", "build", "--lib")
    environment("CARGO_BUILD_JOBS", "4")
    inputs.dir(rustDir.resolve("src"))
    inputs.files(rustDir.resolve("Cargo.toml"), rustDir.resolve("Cargo.lock"))
    // Path dependencies: agenda-core / engine-mobile / ark-core sources.
    inputs.dir("/workspace/wt/agenda-gpui-android/crates/agenda-core/src")
    inputs.dir("/workspace/wt/cortex-android/runtime/crates/engine-mobile/src")
    inputs.dir("/workspace/wt/cortex-android/core/crates/ark-core/src")
    outputs.file(rustDir.resolve("target/debug/libagenda_mobile.so"))
}

val generateUniffiBindings = tasks.register<Exec>("generateUniffiBindings") {
    dependsOn(buildRustHost)
    workingDir = rustDir
    commandLine(
        "cargo", "run", "--features", "cli", "--bin", "uniffi-bindgen", "--",
        "generate", "--library", rustDir.resolve("target/debug/libagenda_mobile.so"),
        "--language", "kotlin",
        "--out-dir", generatedDir.get().asFile,
    )
    environment("CARGO_BUILD_JOBS", "4")
    inputs.file(rustDir.resolve("target/debug/libagenda_mobile.so"))
    outputs.dir(generatedDir)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

sourceSets {
    named("main") {
        java.srcDir(generatedDir)
    }
}

tasks.named("compileKotlin") {
    dependsOn(generateUniffiBindings)
}

dependencies {
    api(libs.jna)
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
}

tasks.test {
    dependsOn(buildRustHost)
    systemProperty("jna.library.path", rustDir.resolve("target/debug").absolutePath)
    testLogging {
        events("passed", "failed", "skipped")
    }
}
