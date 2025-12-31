import org.gradle.api.initialization.IncludedBuild
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    alias(libs.plugins.xdk.build.properties)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

// Access JDK versions from xdkProperties
// For mixed Java/Kotlin projects, use Kotlin JDK for both to avoid target mismatch
val kotlinJdkVersion = xdkProperties.int("org.xtclang.kotlin.jdk")

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

// Configure DSL source set
sourceSets {
    main {
        kotlin {
            srcDir("dsl")
        }
    }
}

dependencies {
    // LSP4J - Eclipse LSP implementation for Java (specialized, not in version catalog)
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:0.21.1")
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j.jsonrpc:0.21.1")

    // Kotlin serialization for DSL model
    implementation(libs.kotlinx.serialization.json)

    // JSpecify for nullability annotations
    implementation(libs.jspecify)

    // Logging
    implementation(libs.slf4j.api)
    runtimeOnly(libs.logback)

    // Testing
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.assertj)
    testImplementation(libs.mockito)
    testImplementation(libs.awaitility)
}

application {
    mainClass.set("org.xvm.lsp.server.XtcLanguageServerLauncher")
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

// Create a fat JAR for distribution
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

    manifest {
        attributes("Main-Class" to "org.xvm.lsp.server.XtcLanguageServerLauncher")
    }
}

// =============================================================================
// TextMate Grammar Generation
// =============================================================================
// The DSL in dsl/ defines the XTC language model (XtcLanguage.kt).
// This task runs the generator to produce TextMate grammar as build output.

val generatedTextMateDir = layout.buildDirectory.dir("generated/textmate")

// Capture classpath and output dirs as FileCollection (CC-compatible)
val generatorClasspath: FileCollection = configurations.runtimeClasspath.get()
val compiledClasses: FileCollection = sourceSets.main.get().output.classesDirs

// Capture DSL source files as FileCollection (CC-compatible)
val dslSourceFiles: FileCollection = layout.projectDirectory.dir("dsl").asFileTree.matching {
    include("**/*.kt")
}

// Create output directory before generation tasks run
val createTextMateDir by tasks.registering {
    val outputDir = generatedTextMateDir
    doLast {
        outputDir.get().asFile.mkdirs()
    }
}

val generateTextMate by tasks.registering(JavaExec::class) {
    group = "generation"
    description = "Generate TextMate grammar from the XTC language model"

    dependsOn(tasks.named("compileKotlin"), tasks.named("compileJava"), createTextMateDir)

    classpath(generatorClasspath)
    classpath(compiledClasses)
    mainClass.set("org.xtclang.tooling.LanguageModelCliKt")

    // Capture output path at configuration time
    val outputPath = generatedTextMateDir.get().file("xtc.tmLanguage.json").asFile.absolutePath
    args("textmate", outputPath)

    inputs.files(dslSourceFiles)
    outputs.dir(generatedTextMateDir)
}

// Generate VS Code language configuration alongside TextMate grammar
val generateLanguageConfig by tasks.registering(JavaExec::class) {
    group = "generation"
    description = "Generate VS Code language configuration from the XTC language model"

    dependsOn(tasks.named("compileKotlin"), tasks.named("compileJava"), createTextMateDir)

    classpath(generatorClasspath)
    classpath(compiledClasses)
    mainClass.set("org.xtclang.tooling.LanguageModelCliKt")

    // Capture output path at configuration time
    val outputPath = generatedTextMateDir.get().file("language-configuration.json").asFile.absolutePath
    args("vscode-config", outputPath)

    inputs.files(dslSourceFiles)
    outputs.file(generatedTextMateDir.map { it.file("language-configuration.json") })
}

// Combine all generation tasks
val generateEditorSupport by tasks.registering {
    group = "generation"
    description = "Generate all editor support files (TextMate grammar, language config)"
    dependsOn(generateTextMate, generateLanguageConfig)
}

// =============================================================================
// Aggregate intellij-plugin build into lang lifecycle tasks
// =============================================================================
// The intellij-plugin is an included build that depends on generated TextMate grammar.
// We ensure grammar is generated first, then build the plugin.
val intellijPlugin: IncludedBuild = gradle.includedBuild("intellij-plugin")

// Ensure plugin build tasks run AFTER grammar generation
gradle.taskGraph.whenReady {
    val pluginTasks = allTasks.filter { it.project.name == "xtc-intellij-plugin" }
    val genTask = allTasks.find { it.name == "generateEditorSupport" && it.project.name == "xtc-lsp" }
    if (genTask != null) {
        pluginTasks.forEach { pluginTask ->
            pluginTask.mustRunAfter(genTask)
        }
    }
}

val build by tasks.existing {
    // Generate grammar first (explicit dependency), then build plugin
    dependsOn(generateEditorSupport)
    dependsOn(intellijPlugin.task(":buildPlugin"))
}

val assemble by tasks.existing {
    dependsOn(intellijPlugin.task(":assemble"))
}

val check by tasks.existing {
    dependsOn(intellijPlugin.task(":check"))
}

val clean by tasks.existing {
    dependsOn(intellijPlugin.task(":clean"))
}