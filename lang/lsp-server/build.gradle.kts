import org.gradle.jvm.toolchain.JavaLanguageVersion
import java.time.Instant

plugins {
    alias(libs.plugins.xdk.build.properties)
    `java-library`
}

// IntelliJ 2025.1 runs on JDK 21, so LSP server must target JDK 21 for in-process execution
val intellijJdkVersion = libs.versions.intellij.jdk.get().toInt()

// Generate build info for version verification
val generateBuildInfo by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/resources/buildinfo")
    val buildTime = Instant.now().toString()
    val projectVersion = project.version.toString()  // Capture at configuration time
    outputs.dir(outputDir)
    doLast {
        val outFile = outputDir.get().file("lsp-version.properties").asFile
        outFile.parentFile.mkdirs()
        outFile.writeText("lsp.build.time=$buildTime\nlsp.version=$projectVersion\n")
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

repositories {
    mavenCentral()
}

dependencies {
    // LSP4J - Eclipse LSP implementation for Java
    // Use compileOnly because LSP4IJ provides LSP4J at runtime.
    // Bundling our own version causes ClassCastException due to classloader conflicts.
    compileOnly(libs.lsp4j)
    compileOnly(libs.lsp4j.jsonrpc)

    // JSpecify for nullability annotations
    compileOnly(libs.jspecify)

    // Logging - compileOnly since IntelliJ provides SLF4J
    compileOnly(libs.slf4j.api)

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

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

tasks.jar {
    manifest {
        attributes(
            "Main-Class" to "org.xvm.lsp.server.XtcLanguageServerLauncher"
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
        attributes("Main-Class" to "org.xvm.lsp.server.XtcLanguageServerLauncher")
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
