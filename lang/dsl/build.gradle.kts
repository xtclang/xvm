plugins {
    alias(libs.plugins.xdk.build.properties)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
}

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

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.slf4j.api)
    runtimeOnly(libs.logback)
    testImplementation(libs.junit.jupiter)
    testImplementation(kotlin("test"))
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

// =============================================================================
// Editor Support Generation
// =============================================================================

val generatedDir: Provider<Directory> = layout.buildDirectory.dir("generated")

/**
 * Configure a generator task with common settings.
 * Each task declares its specific output file(s) to avoid overlapping output claims.
 */
fun JavaExec.configureGenerator(
    command: String,
    vararg outputFileNames: String,
) {
    group = "generation"
    dependsOn(tasks.compileKotlin, tasks.processResources)
    classpath = configurations.runtimeClasspath.get() + sourceSets.main.get().output
    mainClass.set("org.xtclang.tooling.LanguageModelCliKt")
    // Pass project version to CLI so generated files have correct version metadata
    val projectVersion = project.version.toString()
    systemProperty("project.version", projectVersion)
    // Declare version as input for proper Gradle caching
    inputs.property("projectVersion", projectVersion)

    val outputPath =
        if (outputFileNames.isNotEmpty()) {
            generatedDir
                .get()
                .file(outputFileNames.first())
                .asFile.absolutePath
        } else {
            generatedDir.get().asFile.absolutePath
        }
    args(command, outputPath)

    inputs.files(
        sourceSets.main
            .get()
            .kotlin.sourceDirectories,
    )
    // Declare specific output files to avoid overlapping claims
    outputFileNames.forEach { fileName ->
        outputs.file(generatedDir.map { it.file(fileName) })
    }
}

val generateTextMate by tasks.registering(JavaExec::class) {
    description = "Generate TextMate grammar from the XTC language model"
    configureGenerator("textmate", "xtc.tmLanguage.json")
}

val generateLanguageConfig by tasks.registering(JavaExec::class) {
    description = "Generate VS Code language configuration"
    configureGenerator("vscode-config", "language-configuration.json")
}

val generatePackageJson by tasks.registering(JavaExec::class) {
    description = "Generate package.json for TextMate bundle"
    configureGenerator("package-json", "package.json")
}

val generateVim by tasks.registering(JavaExec::class) {
    description = "Generate Vim syntax file"
    configureGenerator("vim", "xtc.vim")
}

val generateEmacs by tasks.registering(JavaExec::class) {
    description = "Generate Emacs major mode"
    configureGenerator("emacs", "xtc-mode.el")
}

val generateTreeSitter by tasks.registering(JavaExec::class) {
    group = "generation"
    description = "Generate Tree-sitter grammar and highlights"
    dependsOn(tasks.compileKotlin, tasks.processResources)
    classpath = configurations.runtimeClasspath.get() + sourceSets.main.get().output
    mainClass.set("org.xtclang.tooling.LanguageModelCliKt")
    // Pass project version to CLI so generated files have correct version metadata
    val projectVersion = project.version.toString()
    systemProperty("project.version", projectVersion)
    // Declare version as input for proper Gradle caching
    inputs.property("projectVersion", projectVersion)
    // Tree-sitter expects a directory, not a file
    args("tree-sitter", generatedDir.get().asFile.absolutePath)
    inputs.files(
        sourceSets.main
            .get()
            .kotlin.sourceDirectories,
    )
    inputs.files(
        sourceSets.main
            .get()
            .resources.sourceDirectories,
    )
    outputs.files(
        generatedDir.map { it.file("grammar.js") },
        generatedDir.map { it.file("highlights.scm") },
    )
}

// Generate scanner.c from Kotlin ScannerSpec (single source of truth)
val generateScannerC by tasks.registering(JavaExec::class) {
    group = "generation"
    description = "Generate scanner.c from Kotlin ScannerSpec"
    dependsOn(tasks.compileKotlin, tasks.processResources, generateTreeSitter)
    classpath = configurations.runtimeClasspath.get() + sourceSets.main.get().output
    mainClass.set("org.xtclang.tooling.scanner.ScannerCGeneratorDslKt")
    args(
        generatedDir
            .map {
                it
                    .dir("src")
                    .file("scanner.c")
                    .asFile.absolutePath
            }.get(),
    )
    inputs.files(
        sourceSets.main
            .get()
            .kotlin.sourceDirectories,
    )
    outputs.file(generatedDir.map { it.dir("src").file("scanner.c") })
}

val generateSublime by tasks.registering(JavaExec::class) {
    description = "Generate Sublime Text syntax file"
    configureGenerator("sublime", "xtc.sublime-syntax")
}

val generateEditorSupport by tasks.registering {
    group = "generation"
    description = "Generate all editor support files"
    dependsOn(
        generateTextMate,
        generateLanguageConfig,
        generatePackageJson,
        generateVim,
        generateEmacs,
        generateTreeSitter,
        generateSublime,
    )
}

// =============================================================================
// Tree-sitter Native Tasks
// =============================================================================
// Native library building, validation, and testing are in the :lang:tree-sitter
// subproject. This keeps the DSL project focused on generation.
//
// Run: ./gradlew :lang:tree-sitter:testTreeSitterParse
// Run: ./gradlew :lang:tree-sitter:buildAllNativeLibrariesOnDemand
// =============================================================================

// =============================================================================
// Consumable configuration for other projects
// =============================================================================

// TODO: Right now we only provide an export point for textMate, since IntelliJ and VS Code plugins use
//   it until we have integrated semantic tokens in the LSP, which requires rewrite of at least the Lexer.t
val textMateElements by configurations.registering {
    isCanBeConsumed = true
    isCanBeResolved = false
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.DOCUMENTATION))
        attribute(Usage.USAGE_ATTRIBUTE, objects.named("textmate-grammar"))
    }
    outgoing {
        // Expose individual files with their generating tasks as dependencies
        artifact(generatedDir.map { it.file("xtc.tmLanguage.json") }) {
            builtBy(generateTextMate)
        }
        artifact(generatedDir.map { it.file("language-configuration.json") }) {
            builtBy(generateLanguageConfig)
        }
        // package.json is required for IntelliJ TextMate plugin to recognize the bundle
        artifact(generatedDir.map { it.file("package.json") }) {
            builtBy(generatePackageJson)
        }
    }
}
