import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.time.Instant

plugins {
    alias(libs.plugins.xdk.build.properties)
    alias(libs.plugins.kotlin.jvm)
    `java-library`
}

// =============================================================================
// DESIGN DECISION: Kotlin for LSP Server
// =============================================================================
// The LSP server is implemented in Kotlin (not Java) for the following reasons:
//
// 1. CONSISTENCY: The language model (XtcLanguage.kt) and all generators are Kotlin.
//    Having the LSP server in Kotlin means one language throughout the tooling.
//
// 2. DSL CAPABILITIES: Tree-sitter query patterns and builder APIs benefit from
//    Kotlin's DSL features (extension functions, lambdas, infix functions).
//
// 3. NULL SAFETY: Kotlin's null safety is built-in vs requiring @Nullable annotations.
//    This is critical for LSP where null responses have semantic meaning.
//
// 4. COROUTINES: Kotlin coroutines provide cleaner async handling than
//    CompletableFuture for LSP's request/response model.
//
// 5. INTEROP: Full Java interop means LSP4J (Java library) works seamlessly.
//    The compiled JAR is identical in structure - consumers see no difference.
//
// The Kotlin stdlib (~1.5MB) is bundled in the fat JAR automatically.
// =============================================================================

// IntelliJ 2025.1 runs on JDK 21, so LSP server must target JDK 21 for in-process execution
val intellijJdkVersion = libs.versions.intellij.jdk.get().toInt()

// =============================================================================
// LSP Adapter Selection
// =============================================================================
// The LSP server can use different parsing backends:
//
//   mock        - Regex-based parsing (default, no native dependencies)
//   treesitter  - Tree-sitter parsing (syntax-level intelligence, needs native lib)
//
// Set via Gradle property: -Plsp.adapter=treesitter
// Or in gradle.properties:  lsp.adapter=treesitter
//
// Default is 'mock' which provides basic functionality without tree-sitter setup.
// Use 'treesitter' for full syntax-aware features (requires native library compiled).
// =============================================================================
val lspAdapter = project.findProperty("lsp.adapter")?.toString() ?: "mock"

// Generate build info for version verification and adapter selection
val generateBuildInfo by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/resources/buildinfo")
    val buildTime = Instant.now().toString()
    val projectVersion = project.version.toString()  // Capture at configuration time
    val adapter = lspAdapter  // Capture at configuration time
    outputs.dir(outputDir)
    doLast {
        val outFile = outputDir.get().file("lsp-version.properties").asFile
        outFile.parentFile.mkdirs()
        outFile.writeText("""
            lsp.build.time=$buildTime
            lsp.version=$projectVersion
            lsp.adapter=$adapter
        """.trimIndent() + "\n")
    }
}

sourceSets.main {
    resources.srcDir(generateBuildInfo.map { layout.buildDirectory.dir("generated/resources/buildinfo") })
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(intellijJdkVersion))
    }
}

kotlin {
    jvmToolchain(intellijJdkVersion)
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(intellijJdkVersion.toString()))
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // LSP4J - Eclipse LSP implementation for Java
    // Use compileOnly because LSP4IJ provides LSP4J at runtime.
    // Bundling our own version causes ClassCastException due to classloader conflicts.
    compileOnly(libs.lsp4j)
    compileOnly(libs.lsp4j.jsonrpc)

    // JetBrains annotations for nullability (Kotlin uses these automatically)
    compileOnly(libs.jetbrains.annotations)

    // Logging - compileOnly since IntelliJ provides SLF4J
    compileOnly(libs.slf4j.api)

    // Tree-sitter JVM bindings for fast, incremental, error-tolerant parsing
    // This provides syntax-level intelligence (symbols, completion, folding) without compiler
    implementation(libs.jtreesitter)

    // Testing - LSP4J and SLF4J needed for test compilation/runtime (compileOnly doesn't expose to tests)
    testImplementation(libs.lsp4j)
    testImplementation(libs.lsp4j.jsonrpc)
    testRuntimeOnly(libs.slf4j.simple)
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.assertj)
    testImplementation(libs.mockito)
    testImplementation(libs.awaitility)
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:deprecation")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

tasks.jar {
    manifest {
        attributes(
            "Main-Class" to "org.xvm.lsp.server.XtcLanguageServerLauncherKt"
        )
    }
}

// =============================================================================
// Fat JAR for distribution
// =============================================================================
// Creates a self-contained JAR with all dependencies that can be launched
// by any IDE (IntelliJ, VS Code, etc.) as a language server process.

val fatJar by tasks.registering(Jar::class) {
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    from(sourceSets.main.get().output)

    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith("jar") }
            .map { zipTree(it) }
    })

    // Exclude signature files from dependencies (they become invalid in fat JAR)
    exclude("META-INF/*.RSA", "META-INF/*.SF", "META-INF/*.DSA")

    manifest {
        attributes("Main-Class" to "org.xvm.lsp.server.XtcLanguageServerLauncherKt")
    }
}

// =============================================================================
// Consumable configuration for IDE plugins
// =============================================================================
// This configuration exposes the fat JAR as an artifact that other projects
// (like intellij-plugin) can depend on through proper Gradle configuration.

val lspServerElements by configurations.registering {
    isCanBeConsumed = true
    isCanBeResolved = false
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.SHADOWED))
    }
    outgoing {
        artifact(fatJar)
    }
}

// Ensure fatJar is built when assembling
val assemble by tasks.existing {
    dependsOn(fatJar)
}
