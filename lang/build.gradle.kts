plugins {
    java
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

group = "org.xvm"
version = "0.1.0-SNAPSHOT"

// Use JDK 25 toolchain; target JVM 23 bytecode (Kotlin 2.1.0's max supported target)
// This allows using JDK 25 features while maintaining Kotlin compatibility
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

kotlin {
    jvmToolchain(25)
    sourceSets {
        main {
            kotlin.srcDir("dsl")
        }
    }
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_23)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(23)
}

repositories {
    mavenCentral()
}

dependencies {
    // Kotlin standard library and serialization
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.serialization.json)

    // LSP4J - Eclipse LSP implementation for Java
    implementation(libs.lsp4j)
    implementation(libs.lsp4j.jsonrpc)

    // JSpecify for nullability annotations
    implementation(libs.jspecify)

    // Logging
    implementation(libs.slf4j.api)
    runtimeOnly(libs.logback.classic)

    // Testing
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.assertj.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.awaitility)
    testImplementation(libs.kotlin.test)
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
// Language Model Tasks
// =============================================================================

// Task to dump the XTC language model as JSON
val dumpLanguageModel by tasks.registering(JavaExec::class) {
    group = "language-support"
    description = "Dump the XTC language model as JSON"
    mainClass.set("org.xtclang.tooling.LanguageModelCliKt")
    classpath = sourceSets.main.get().runtimeClasspath
    args = listOf("dump")
}

// Task to generate TextMate grammar from the language model
val generateTextMate by tasks.registering(JavaExec::class) {
    group = "language-support"
    description = "Generate TextMate grammar from the XTC language model"
    mainClass.set("org.xtclang.tooling.LanguageModelCliKt")
    classpath = sourceSets.main.get().runtimeClasspath
    args = listOf("textmate")
    outputs.file(layout.buildDirectory.file("generated/xtc.tmLanguage.json"))
}

// Task to show language model statistics
val languageStats by tasks.registering(JavaExec::class) {
    group = "language-support"
    description = "Show statistics about the XTC language model"
    mainClass.set("org.xtclang.tooling.LanguageModelCliKt")
    classpath = sourceSets.main.get().runtimeClasspath
    args = listOf("stats")
}

// Task to validate against real source files
val validateSources by tasks.registering(JavaExec::class) {
    group = "language-support"
    description = "Validate language model against real XTC source files"
    mainClass.set("org.xtclang.tooling.LanguageModelCliKt")
    classpath = sourceSets.main.get().runtimeClasspath
    args = listOf("validate", "../lib_ecstasy/src/main/x")
}

// =============================================================================
// Editor-Specific Generators
// =============================================================================

val generatedDir = layout.buildDirectory.dir("generated")

// Task to generate Vim syntax file
val generateVim by tasks.registering(JavaExec::class) {
    group = "language-support"
    description = "Generate Vim syntax file from the XTC language model"
    mainClass.set("org.xtclang.tooling.LanguageModelCliKt")
    classpath = sourceSets.main.get().runtimeClasspath
    args = listOf("vim", generatedDir.get().file("xtc.vim").asFile.absolutePath)
    outputs.file(generatedDir.map { it.file("xtc.vim") })
}

// Task to generate Emacs major mode
val generateEmacs by tasks.registering(JavaExec::class) {
    group = "language-support"
    description = "Generate Emacs major mode from the XTC language model"
    mainClass.set("org.xtclang.tooling.LanguageModelCliKt")
    classpath = sourceSets.main.get().runtimeClasspath
    args = listOf("emacs", generatedDir.get().file("xtc-mode.el").asFile.absolutePath)
    outputs.file(generatedDir.map { it.file("xtc-mode.el") })
}

// Task to generate Tree-sitter grammar
val generateTreeSitter by tasks.registering(JavaExec::class) {
    group = "language-support"
    description = "Generate Tree-sitter grammar from the XTC language model"
    mainClass.set("org.xtclang.tooling.LanguageModelCliKt")
    classpath = sourceSets.main.get().runtimeClasspath
    args = listOf("tree-sitter", generatedDir.get().asFile.absolutePath)
    outputs.files(
        generatedDir.map { it.file("grammar.js") },
        generatedDir.map { it.file("highlights.scm") }
    )
}

// Task to generate VS Code language configuration
val generateVSCodeConfig by tasks.registering(JavaExec::class) {
    group = "language-support"
    description = "Generate VS Code language configuration from the XTC language model"
    mainClass.set("org.xtclang.tooling.LanguageModelCliKt")
    classpath = sourceSets.main.get().runtimeClasspath
    args = listOf("vscode-config", generatedDir.get().file("language-configuration.json").asFile.absolutePath)
    outputs.file(generatedDir.map { it.file("language-configuration.json") })
}

// Task to generate all editor support files
val generateAllEditorSupport by tasks.registering {
    group = "language-support"
    description = "Generate all editor support files from the XTC language model"
    dependsOn(generateTextMate, generateVim, generateEmacs, generateTreeSitter, generateVSCodeConfig)
}
