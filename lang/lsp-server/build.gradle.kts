import org.gradle.jvm.toolchain.JavaLanguageVersion
import java.time.Instant

plugins {
    alias(libs.plugins.xdk.build.properties)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktlint)
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

// =============================================================================
// OUT-OF-PROCESS EXECUTION
// =============================================================================
// The LSP server runs as a SEPARATE PROCESS from IntelliJ, with its own JRE.
// This allows using jtreesitter 0.26+ (requires Java 23+) even though IntelliJ
// uses JBR 21. The intellij-plugin spawns this server and communicates via stdio.
//
// See doc/plans/PLAN_OUT_OF_PROCESS_LSP.md for architecture details.
// =============================================================================

// Use the same Kotlin JDK version as the rest of the XDK (from version.properties)
val kotlinJdkVersion = xdkProperties.int("org.xtclang.kotlin.jdk")

// =============================================================================
// LSP Adapter Selection
// =============================================================================
// The LSP server can use different parsing backends:
//
//   treesitter  - Tree-sitter parsing (DEFAULT, syntax-level intelligence, needs native lib)
//   mock        - Regex-based parsing (no native dependencies, for testing/fallback)
//
// Set via Gradle property: -Plsp.adapter=mock (to override default)
// Or in gradle.properties:  lsp.adapter=mock
//
// Default is 'treesitter' which provides syntax-aware features (native library bundled).
// Use 'mock' for basic regex-based functionality if tree-sitter has issues.
// =============================================================================
val lspAdapter: String = project.findProperty("lsp.adapter")?.toString() ?: "treesitter"
logger.lifecycle("LSP Server adapter: $lspAdapter")

// Generate build info for version verification and adapter selection
val generateBuildInfo by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/resources/buildinfo")
    val buildTime = Instant.now().toString()
    val projectVersion = project.version.toString() // Capture at configuration time
    val adapter = lspAdapter // Capture at configuration time

    // Declare inputs so task re-runs when adapter changes
    inputs.property("adapter", adapter)
    inputs.property("version", projectVersion)
    outputs.dir(outputDir)

    doLast {
        val outFile = outputDir.get().file("lsp-version.properties").asFile
        outFile.parentFile.mkdirs()
        outFile.writeText(
            """
            lsp.build.time=$buildTime
            lsp.version=$projectVersion
            lsp.adapter=$adapter
            """.trimIndent() + "\n",
        )
    }
}

sourceSets.main {
    resources.srcDir(generateBuildInfo.map { layout.buildDirectory.dir("generated/resources/buildinfo") })
}

java {
    toolchain {
        languageVersion.set(kotlinJdkVersion.map { JavaLanguageVersion.of(it) })
    }
}

kotlin {
    jvmToolchain {
        languageVersion.set(kotlinJdkVersion.map { JavaLanguageVersion.of(it) })
    }
}

repositories {
    mavenCentral()
}

// =============================================================================
// Native Library from Tree-sitter
// =============================================================================
// Consume the tree-sitter native library for the current platform.
// This library is built on-demand using Zig cross-compilation.

val treeSitterNativeLib: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(Usage.USAGE_ATTRIBUTE, objects.named("native-library"))
    }
}

dependencies {
    // Native library from tree-sitter project
    treeSitterNativeLib(project(path = ":tree-sitter", configuration = "nativeLibraryElements"))

    // LSP4J - Eclipse LSP implementation for Java
    // Bundled in fat JAR for out-of-process execution (not provided by IntelliJ)
    implementation(libs.lsp4j)
    implementation(libs.lsp4j.jsonrpc)

    // JetBrains annotations for nullability (Kotlin uses these automatically)
    compileOnly(libs.jetbrains.annotations)

    // Logging - bundled in fat JAR for out-of-process execution
    implementation(libs.slf4j.api)
    implementation(libs.logback)

    // Tree-sitter JVM bindings for fast, incremental, error-tolerant parsing
    // Requires Java 23+ (uses FFM API) - runs out-of-process with its own JRE
    implementation(libs.jtreesitter)

    // Testing
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.assertj)
    testImplementation(libs.mockito)
    testImplementation(libs.awaitility)
}

// =============================================================================
// Copy Native Libraries to Resources
// =============================================================================
// Copy tree-sitter native libraries for ALL platforms to the resources directory
// so they can be bundled in the JAR. The runtime loader will select the appropriate
// library based on the current platform.

val copyNativeLibToResources by tasks.registering(Copy::class) {
    group = "build"
    description = "Copy tree-sitter native libraries for all platforms to resources"

    from(treeSitterNativeLib)
    into(layout.buildDirectory.dir("generated/resources/native"))
}

// Add native library resources to source sets
sourceSets.main {
    resources.srcDir(copyNativeLibToResources.map { layout.buildDirectory.dir("generated/resources") })
}

// Ensure native library is copied before processResources
val processResources by tasks.existing {
    dependsOn(copyNativeLibToResources)
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Xlint:deprecation")
}

// =============================================================================
// Ensure ktlint runs during normal development
// =============================================================================
// By default, ktlint only runs as part of 'check', not during compilation.
// This means running 'runIde', 'jar', or 'assemble' skips ktlint entirely.
// We fix this by making compileKotlin depend on ktlintCheck, so any build
// that compiles code also verifies formatting.
val ktlintCheck by tasks.existing
val compileKotlin by tasks.existing {
    dependsOn(ktlintCheck)
}
val classes by tasks.existing

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

tasks.jar {
    manifest {
        attributes(
            "Main-Class" to "org.xvm.lsp.server.XtcLanguageServerLauncherKt",
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

    // Explicit dependency ensures resources (logback.xml, etc.) are processed before JAR creation
    dependsOn(classes)

    from(sourceSets.main.get().output)

    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath
            .get()
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

// Configuration to expose version properties to IDE plugins
// This allows the IntelliJ plugin to display version info without accessing the LSP JAR
val lspVersionProperties by configurations.registering {
    isCanBeConsumed = true
    isCanBeResolved = false
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.DOCUMENTATION))
        attribute(Usage.USAGE_ATTRIBUTE, objects.named("lsp-version-properties"))
    }
    outgoing {
        artifact(generateBuildInfo.map { layout.buildDirectory.file("generated/resources/buildinfo/lsp-version.properties") }) {
            builtBy(generateBuildInfo)
        }
    }
}

// Ensure fatJar is built when assembling
val assemble by tasks.existing {
    dependsOn(fatJar)
}
