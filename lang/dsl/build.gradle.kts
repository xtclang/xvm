import de.undercouch.gradle.tasks.download.Download
import org.gradle.api.DefaultTask
import org.gradle.api.file.Directory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.process.ExecOperations
import java.io.File
import java.util.zip.GZIPInputStream
import javax.inject.Inject

plugins {
    alias(libs.plugins.xdk.build.properties)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.download)
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
fun JavaExec.configureGenerator(command: String, vararg outputFileNames: String) {
    group = "generation"
    dependsOn(tasks.compileKotlin, tasks.processResources)
    classpath = configurations.runtimeClasspath.get() + sourceSets.main.get().output
    mainClass.set("org.xtclang.tooling.LanguageModelCliKt")

    val outputPath = if (outputFileNames.isNotEmpty()) {
        generatedDir.get().file(outputFileNames[0]).asFile.absolutePath
    } else {
        generatedDir.get().asFile.absolutePath
    }
    args(command, outputPath)

    inputs.files(sourceSets.main.get().kotlin.sourceDirectories)
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

val generatePackageJson by tasks.registering {
    group = "generation"
    description = "Generate package.json for TextMate bundle"
    val outputFile = generatedDir.map { it.file("package.json").asFile }
    outputs.file(outputFile)
    inputs.files(sourceSets.main.get().kotlin.sourceDirectories)
    doLast {
        val file = outputFile.get()
        file.parentFile.mkdirs()
        file.writeText("""
            {
                "name": "xtc-language",
                "displayName": "XTC Language",
                "description": "XTC (Ecstasy) language support",
                "version": "1.0.0",
                "engines": { "vscode": "^1.50.0" },
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
        """.trimIndent())
    }
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
    // Tree-sitter expects a directory, not a file
    args("tree-sitter", generatedDir.get().asFile.absolutePath)
    inputs.files(sourceSets.main.get().kotlin.sourceDirectories)
    inputs.files(sourceSets.main.get().resources.sourceDirectories)
    outputs.files(
        generatedDir.map { it.file("grammar.js") },
        generatedDir.map { it.file("highlights.scm") }
    )
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
        generateSublime)
}

// =============================================================================
// Tree-sitter Grammar Validation
// =============================================================================
// These tasks validate and test the generated tree-sitter grammar.
// The tree-sitter CLI is auto-downloaded using the download plugin.
// =============================================================================

val treeSitterCliVersion: String = libs.versions.tree.sitter.cli.get()
val treeSitterCliDir: Provider<Directory> = layout.buildDirectory.dir("tree-sitter-cli")

// Determine platform for tree-sitter CLI download
val treeSitterPlatform: String = run {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch")
    when {
        os.contains("mac") && (arch == "aarch64" || arch == "arm64") -> "macos-arm64"
        os.contains("mac") -> "macos-x64"
        os.contains("linux") && (arch == "amd64" || arch == "x86_64") -> "linux-x64"
        os.contains("linux") && (arch == "aarch64" || arch == "arm64") -> "linux-arm64"
        else -> "unsupported"
    }
}
val treeSitterPlatformSupported = treeSitterPlatform != "unsupported"

/**
 * Download tree-sitter CLI gzipped binary for the current platform.
 */
val downloadTreeSitterCliGz by tasks.registering(Download::class) {
    group = "tree-sitter"
    description = "Download tree-sitter CLI gzipped binary"
    enabled = treeSitterPlatformSupported

    src("https://github.com/tree-sitter/tree-sitter/releases/download/v$treeSitterCliVersion/tree-sitter-$treeSitterPlatform.gz")
    dest(layout.buildDirectory.dir("tree-sitter-cli"))
    overwrite(false)
    onlyIfModified(true)
    quiet(false)
}

/**
 * Extract the tree-sitter CLI from the downloaded gzip file.
 * Uses Java's built-in GZIPInputStream for platform independence (no external gunzip required).
 */
val extractTreeSitterCli by tasks.registering {
    group = "tree-sitter"
    description = "Extract tree-sitter CLI from gzip"
    dependsOn(downloadTreeSitterCliGz)
    enabled = treeSitterPlatformSupported

    val gzFile = treeSitterCliDir.map { it.file("tree-sitter-$treeSitterPlatform.gz").asFile }
    val outputFile = treeSitterCliDir.map { it.file("tree-sitter").asFile }

    inputs.file(gzFile)
    outputs.file(outputFile)

    doLast {
        val input = gzFile.get()
        val output = outputFile.get()

        GZIPInputStream(input.inputStream().buffered()).use { gzIn ->
            output.outputStream().buffered().use { out ->
                gzIn.copyTo(out)
            }
        }

        output.setExecutable(true)
        this.logger.lifecycle("Extracted tree-sitter CLI to: ${output.absolutePath}")
    }
}

/**
 * Validate that the generated tree-sitter grammar compiles.
 * This runs `tree-sitter generate` which validates the grammar.js and produces parser.c
 */
val validateTreeSitterGrammar by tasks.registering(Exec::class) {
    group = "tree-sitter"
    description = "Validate tree-sitter grammar compiles"
    dependsOn(generateTreeSitter, extractTreeSitterCli)
    enabled = treeSitterPlatformSupported

    workingDir(generatedDir)
    executable(treeSitterCliDir.map { it.file("tree-sitter").asFile.absolutePath }.get())
    args("generate")
}

/**
 * Test parsing XTC files from the XDK libraries using tree-sitter CLI.
 * Uses the 675+ .x files in lib_* directories as a comprehensive test corpus.
 */
abstract class TreeSitterParseTestTask @Inject constructor(
    private val execOps: ExecOperations
) : DefaultTask() {

    @get:Input
    abstract val cliPath: Property<String>

    @get:Input
    abstract val workDir: Property<File>

    @get:Input
    abstract val libDirs: ListProperty<File>

    @get:Input
    abstract val rootDir: Property<File>

    @TaskAction
    fun run() {
        val xtcFiles = libDirs.get().flatMap { libDir ->
            libDir.walkTopDown()
                .filter { it.isFile && it.extension == "x" }
                .toList()
        }

        if (xtcFiles.isEmpty()) {
            logger.warn("No .x files found in lib_* directories")
            return
        }

        logger.lifecycle("Testing tree-sitter parser on ${xtcFiles.size} XTC files...")

        var passed = 0
        var failed = 0
        val failures = mutableListOf<String>()

        for (file in xtcFiles) {
            val result = execOps.exec {
                workingDir(workDir.get())
                executable(cliPath.get())
                args("parse", "--quiet", file.absolutePath)
                isIgnoreExitValue = true
            }

            if (result.exitValue == 0) {
                passed++
            } else {
                failed++
                failures.add(file.relativeTo(rootDir.get()).path)
            }
        }

        logger.lifecycle("Tree-sitter parse results: $passed passed, $failed failed")

        if (failures.isNotEmpty()) {
            logger.lifecycle("Failed files (first 20):")
            failures.take(20).forEach { logger.lifecycle("  - $it") }
            if (failures.size > 20) {
                logger.lifecycle("  ... and ${failures.size - 20} more")
            }
        }
    }
}

val testTreeSitterParse by tasks.registering(TreeSitterParseTestTask::class) {
    group = "tree-sitter"
    description = "Test tree-sitter parsing on XDK library files"
    dependsOn(validateTreeSitterGrammar)
    enabled = treeSitterPlatformSupported

    // Use XdkPropertiesService to find composite root (works for included builds)
    val compositeRoot = XdkPropertiesService.compositeRootDirectory(projectDir)

    cliPath.set(treeSitterCliDir.map { it.file("tree-sitter").asFile.absolutePath })
    workDir.set(generatedDir.map { it.asFile })
    rootDir.set(compositeRoot)

    // Find all lib_* directories (the XDK standard library)
    val xdkLibDirs = compositeRoot.listFiles { f ->
        f.isDirectory && f.name.startsWith("lib_")
    }?.toList() ?: emptyList()
    libDirs.set(xdkLibDirs)
}

/**
 * Build native tree-sitter library for the current platform.
 * Result: libtree-sitter-xtc.so (Linux) or .dylib (macOS)
 * These should be committed to lang/lsp-server/src/main/resources/native/
 */
val buildTreeSitterLibrary by tasks.registering(Exec::class) {
    group = "tree-sitter"
    description = "Build native tree-sitter library"
    dependsOn(validateTreeSitterGrammar)
    enabled = treeSitterPlatformSupported

    val outputDir = generatedDir.map { it.dir("native") }
    outputs.dir(outputDir)

    workingDir(generatedDir)
    executable(treeSitterCliDir.map { it.file("tree-sitter").asFile.absolutePath }.get())
    args("build", "--output", outputDir.map { it.file("libtree-sitter-xtc").asFile.absolutePath }.get())
}

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
