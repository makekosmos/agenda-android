import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

/** Optional local checkouts used via rust/.cargo/config.toml [patch]. */
fun localRustInputs(): List<File> = buildList {
    System.getenv("AGENDA_GPUI_DIR")?.let { add(File(it, "crates/agenda-core/src")) }
    System.getenv("AGENDA_CORTEX_DIR")?.let {
        add(File(it, "runtime/crates/engine-mobile/src"))
        add(File(it, "core/crates/ark-core/src"))
    }
}.filter { it.isDirectory }

val rustDir = rootDir.resolve("rust")
/** Host cdylib name: .so on Linux (CI, Devin box), .dylib on macOS. */
val hostLib = rustDir.resolve(
    "target/debug/libagenda_mobile." +
        if (System.getProperty("os.name").startsWith("Mac")) "dylib" else "so",
)
val generatedDir = layout.buildDirectory.dir("generated/uniffi")

val buildRustHost = tasks.register<Exec>("buildRustHost") {
    workingDir = rustDir
    commandLine("cargo", "build", "--lib")
    environment("CARGO_BUILD_JOBS", "4")
    inputs.dir(rustDir.resolve("src"))
    inputs.files(rustDir.resolve("Cargo.toml"), rustDir.resolve("Cargo.lock"))
    // Rust deps are git deps pinned in Cargo.toml/Cargo.lock. With a local
    // [patch] override, set AGENDA_CORTEX_DIR / AGENDA_GPUI_DIR so edits there
    // re-trigger this task.
    localRustInputs().forEach { inputs.dir(it) }
    outputs.file(hostLib)
}

val generateUniffiBindings = tasks.register<Exec>("generateUniffiBindings") {
    dependsOn(buildRustHost)
    workingDir = rustDir
    commandLine(
        "cargo", "run", "--features", "cli", "--bin", "uniffi-bindgen", "--",
        "generate", "--library", hostLib,
        "--language", "kotlin",
        "--out-dir", generatedDir.get().asFile,
    )
    environment("CARGO_BUILD_JOBS", "4")
    inputs.file(hostLib)
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
