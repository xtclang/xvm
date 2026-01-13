import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    alias(libs.plugins.xdk.build.properties)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

// Access JDK versions from xdkProperties
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

// Configure DSL source set - compiles the language model from dsl/
sourceSets {
    main {
        kotlin {
            srcDir("dsl")
        }
    }
}

dependencies {
    // Kotlin serialization for DSL model
    implementation(libs.kotlinx.serialization.json)

    // Logging for DSL CLI
    implementation(libs.slf4j.api)
    runtimeOnly(libs.logback)
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

    dependsOn(tasks.named("compileKotlin"), createTextMateDir)

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

    dependsOn(tasks.named("compileKotlin"), createTextMateDir)

    classpath(generatorClasspath)
    classpath(compiledClasses)
    mainClass.set("org.xtclang.tooling.LanguageModelCliKt")

    // Capture output path at configuration time
    val outputPath = generatedTextMateDir.get().file("language-configuration.json").asFile.absolutePath
    args("vscode-config", outputPath)

    inputs.files(dslSourceFiles)
    outputs.file(generatedTextMateDir.map { it.file("language-configuration.json") })
}

// Generate package.json for TextMate bundle (required by IntelliJ's TextMate plugin)
val generatePackageJson by tasks.registering {
    group = "generation"
    description = "Generate package.json for TextMate bundle"

    dependsOn(createTextMateDir)

    val outputFile = generatedTextMateDir.get().file("package.json").asFile
    outputs.file(outputFile)
    inputs.files(dslSourceFiles)

    doLast {
        outputFile.writeText(
            """
            {
                "name": "xtc-language",
                "displayName": "XTC Language",
                "description": "XTC (Ecstasy) language support",
                "version": "1.0.0",
                "engines": {
                    "vscode": "^1.50.0"
                },
                "contributes": {
                    "languages": [{
                        "id": "xtc",
                        "aliases": ["XTC", "Ecstasy", "xtc"],
                        "extensions": [".x"],
                        "configuration": "./language-configuration.json"
                    }],
                    "grammars": [{
                        "language": "xtc",
                        "scopeName": "source.xtc",
                        "path": "./xtc.tmLanguage.json"
                    }]
                }
            }
            """.trimIndent()
        )
    }
}

// Combine all generation tasks
val generateEditorSupport by tasks.registering {
    group = "generation"
    description = "Generate all editor support files (TextMate grammar, language config)"
    dependsOn(generateTextMate, generateLanguageConfig, generatePackageJson)
}

// =============================================================================
// Consumable configuration for TextMate grammar
// =============================================================================
// This configuration exposes the generated TextMate files as artifacts that
// other projects (like intellij-plugin) can depend on through Gradle.

val textMateElements by configurations.registering {
    isCanBeConsumed = true
    isCanBeResolved = false
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.DOCUMENTATION))
        attribute(Usage.USAGE_ATTRIBUTE, objects.named("textmate-grammar"))
    }
    outgoing {
        artifact(generatedTextMateDir) {
            builtBy(generateEditorSupport)
        }
    }
}

// =============================================================================
// Aggregate subproject tasks
// =============================================================================

val build by tasks.existing {
    dependsOn(project(":lsp-server").tasks.named("build"))
    dependsOn(generateEditorSupport)
    dependsOn(project(":intellij-plugin").tasks.named("buildPlugin"))
}

val assemble by tasks.existing {
    dependsOn(project(":lsp-server").tasks.named("assemble"))
    dependsOn(project(":intellij-plugin").tasks.named("assemble"))
}

val check by tasks.existing {
    dependsOn(project(":lsp-server").tasks.named("check"))
    dependsOn(project(":intellij-plugin").tasks.named("check"))
}

val clean by tasks.existing {
    dependsOn(project(":lsp-server").tasks.named("clean"))
    dependsOn(project(":intellij-plugin").tasks.named("clean"))
}