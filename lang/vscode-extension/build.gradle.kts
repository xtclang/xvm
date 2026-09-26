import com.github.gradle.node.npm.task.NpmTask
import com.github.gradle.node.task.NodeTask
import groovy.json.JsonOutput
import groovy.json.JsonSlurper

plugins {
    base
    alias(libs.plugins.xdk.build.properties) apply false // Shared build task types
    alias(libs.plugins.lang.node.gradle)
}

node {
    version.set(libs.versions.lang.node.asProvider())
    download.set(true)
}

// Configuration to consume TextMate grammar from root project
val textMateGrammar = configurations.create("textMateGrammar") {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.DOCUMENTATION))
        attribute(Usage.USAGE_ATTRIBUTE, objects.named("textmate-grammar"))
    }
}

dependencies {
    textMateGrammar(project(path = ":dsl", configuration = "textMateElements"))
}

// Copy TextMate grammar files
val copyTextMateGrammar = tasks.register<Copy>("copyTextMateGrammar") {
    description = "Copy TextMate grammar from generated output"
    from(textMateGrammar) {
        include("xtc.tmLanguage.json")
    }
    into(layout.projectDirectory.dir("syntaxes"))
}

// Copy language configuration
val copyLanguageConfig = tasks.register<CopyFileTask>("copyLanguageConfig") {
    description = "Copy language configuration from generated output"
    val languageConfig = textMateGrammar.asFileTree.matching {
        include("language-configuration.json")
    }
    sourceFile.set(layout.file(languageConfig.elements.map { it.single().asFile }))
    outputFile.set(layout.projectDirectory.file("language-configuration.json"))
}

// Copy LSP server fat JAR (self-contained with all dependencies: LSP4J, tree-sitter, Logback)
val copyLspServer = tasks.register<CopyFileTask>("copyLspServer") {
    description = "Copy LSP server fat JAR"
    sourceFile.set(project(":lsp-server").tasks.named<Jar>("fatJar").flatMap { it.archiveFile })
    outputFile.set(layout.projectDirectory.file("server/lsp-server.jar"))
}

// Copy DAP server JAR (bundled for debugging support)
val copyDapServer = tasks.register<CopyFileTask>("copyDapServer") {
    description = "Copy DAP server JAR"
    sourceFile.set(project(":dap-server").tasks.named<Jar>("jar").flatMap { it.archiveFile })
    outputFile.set(layout.projectDirectory.file("server/dap-server.jar"))
}

// Copy LICENSE from repository root
val copyLicense = tasks.register<CopyFileTask>("copyLicense") {
    description = "Copy LICENSE from repository root"
    val compositeRoot = XdkPropertiesService.compositeRootDirectory(projectDir)
    sourceFile.set(File(compositeRoot, "LICENSE.md"))
    outputFile.set(layout.projectDirectory.file("LICENSE.md"))
}

// Generate the marketplace icon (xtc.png, 256x256) and the language file icon
// (xtc-file.png, 32x32) from doc/logo/x.jpg in the repository root. We derive
// PNGs rather than checking them in so the single JPEG source of truth in
// doc/logo/ stays canonical; the `sharp` devDependency handles the conversion.
val generateIcons = tasks.register<NodeTask>("generateIcons") {
    description = "Generate VS Code marketplace and file icons from doc/logo/x.jpg"
    dependsOn(tasks.named("npmInstall"))
    val compositeRoot = XdkPropertiesService.compositeRootDirectory(projectDir)
    val sourceLogo = File(compositeRoot, "doc/logo/x.jpg")
    val outDir = layout.projectDirectory.dir("icons")
    val scriptFile = layout.projectDirectory.file("scripts/generate-icons.cjs")
    script.set(scriptFile.asFile)
    args.set(listOf(sourceLogo.absolutePath, outDir.asFile.absolutePath))
    inputs.file(sourceLogo)
    inputs.file(scriptFile)
    outputs.file(outDir.file("xtc.png"))
    outputs.file(outDir.file("xtc-file.png"))
}

// Configure the plugin-provided npmInstall task (runs `npm install` using the pinned Node)
val npmInstall = tasks.named("npmInstall") {
    mustRunAfter(copyLanguageConfig, copyTextMateGrammar, copyLicense)
}

// Compile TypeScript
val npmCompile = tasks.register<NpmTask>("npmCompile") {
    description = "Compile TypeScript"
    dependsOn(npmInstall)
    args.set(listOf("run", "compile"))

    inputs.dir(layout.projectDirectory.dir("src"))
    inputs.file(layout.projectDirectory.file("tsconfig.json"))
    inputs.files(layout.projectDirectory.file("package.json"), layout.projectDirectory.file("package-lock.json"))
    outputs.dir(layout.projectDirectory.dir("out"))
}

// Bundle separately from tsc's development output so packaging never rewrites npmCompile's files.
val bundledExtension = layout.buildDirectory.file("bundle/extension.js")
val npmBundle = tasks.register<NpmTask>("npmBundle") {
    description = "Bundle extension runtime dependencies for packaging"
    dependsOn(npmInstall)
    args.set(bundledExtension.map { output ->
        listOf(
            "exec", "--no", "--", "esbuild", "src/extension.ts", "--bundle",
            "--outfile=${output.asFile.absolutePath}", "--external:vscode",
            "--platform=node", "--target=node22", "--minify"
        )
    })
    inputs.dir(layout.projectDirectory.dir("src"))
    inputs.files(layout.projectDirectory.file("package.json"), layout.projectDirectory.file("package-lock.json"))
    outputs.file(bundledExtension)
}

// A suffix affects only the staged manifest and resulting artifact, never the source package.json.
val vscodeVersionSuffix = providers.gradleProperty("vscode.version.suffix").orElse("")
val extensionVersion = providers.fileContents(layout.projectDirectory.file("package.json")).asText
    .zip(vscodeVersionSuffix) { content, suffix ->
        val manifest = JsonSlurper().parseText(content) as Map<*, *>
        val version = manifest["version"] as String
        require(suffix.isEmpty() || Regex("[0-9A-Za-z.-]+").matches(suffix)) {
            "vscode.version.suffix must contain only letters, digits, dots and hyphens"
        }
        if (suffix.isEmpty()) version else "${version.substringBefore("-")}-$suffix"
    }

val packageDirectory = layout.buildDirectory.dir("package")
val stagePackage = tasks.register<Sync>("stagePackage") {
    description = "Stage extension files and versioned metadata without modifying sources"
    dependsOn(npmCompile)
    from(layout.projectDirectory) {
        include("package.json", ".vscodeignore", "README.md", "snippets/**")
    }
    from(copyLicense, copyLanguageConfig)
    from(copyTextMateGrammar) { into("syntaxes") }
    from(listOf(copyLspServer, copyDapServer)) { into("server") }
    from(generateIcons) { into("icons") }
    from(npmBundle) { into("out") }
    into(packageDirectory)

    val stagedManifest = packageDirectory.map { it.file("package.json") }
    val packagedVersion = extensionVersion
    inputs.property("extensionVersion", packagedVersion)
    doLast {
        val manifestFile = stagedManifest.get().asFile
        val manifest = (JsonSlurper().parse(manifestFile) as Map<*, *>).toMutableMap()
        manifest["version"] = packagedVersion.get()
        // Gradle already built the icons and bundle; vsce must not run the source-tree npm hook.
        val scripts = manifest["scripts"] as? Map<*, *>
        if (scripts != null) manifest["scripts"] = scripts.filterKeys { it != "vscode:prepublish" }
        manifestFile.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(manifest)) + "\n")
    }
}

val extensionArchive = layout.buildDirectory.file(extensionVersion.map { "distributions/xtc-language-$it.vsix" })
val packageExtension = tasks.register<NodeTask>("packageExtension") {
    description = "Package the staged VS Code extension"
    dependsOn(npmInstall)
    script.set(layout.projectDirectory.file("node_modules/@vscode/vsce/vsce"))
    workingDir.set(layout.dir(stagePackage.map { it.destinationDir }))
    args.set(extensionArchive.map { listOf("package", "--no-dependencies", "--out", it.asFile.absolutePath) })
    inputs.files(stagePackage)
    inputs.file(layout.projectDirectory.file("package-lock.json"))
    outputs.file(extensionArchive)
    val archive = extensionArchive
    doFirst {
        archive.get().asFile.parentFile.mkdirs()
    }
}

// Headless integration test: launches VS Code via @vscode/test-electron with
// the extension loaded from the build tree, opens src/test/fixtures/hello.x,
// and asserts that the .x file is associated with the "xtc" language. This is
// the only way to verify the contributes.languages mapping + the runtime
// setTextDocumentLanguage fallback short of installing the .vsix into the
// user's profile and clicking around manually.
//
// Not wired into `check` by default because on headless Linux runners this
// needs `xvfb-run` (or a similar virtual display). The intent is that local
// developers run it explicitly, and CI opt-in via xvfb if/when desired.
val testVscodeExtension = tasks.register<NpmTask>("testVscodeExtension") {
    group = "verification"
    description = "Run headless integration tests for the VS Code extension"
    dependsOn(npmCompile, copyTextMateGrammar, copyLanguageConfig, copyLspServer, copyDapServer, copyLicense, generateIcons)
    args.set(listOf("run", "test:vscode"))
    // Cache directory used by @vscode/test-electron to keep the downloaded
    // VS Code build across runs; declared as input so a corrupted cache
    // can be cleared by `./gradlew :lang:vscode-extension:clean`.
    inputs.dir(layout.projectDirectory.dir("src/test"))
}

// Main build task - configure the existing task from base plugin
val build = tasks.named("build") {
    dependsOn(packageExtension)
}

// Assemble prepares all resources without packaging
val assemble = tasks.named("assemble") {
    dependsOn(copyTextMateGrammar, copyLanguageConfig, copyLspServer, copyDapServer, copyLicense, npmCompile, generateIcons)
}

// Launch VS Code with extension loaded for testing
val runCode = tasks.register<Exec>("runCode") {
    group = "run"
    description = "Launch VS Code with the extension loaded for testing"
    dependsOn(assemble)

    val extensionPath = layout.projectDirectory.asFile.absolutePath
    val fixturesPath = layout.projectDirectory.dir("src/test/fixtures").asFile.absolutePath
    // Capture PATH + OS at config time so the doFirst stays CC-safe (no
    // System.getenv / System.getProperty calls inside the task action).
    val pathEnv = providers.environmentVariable("PATH").orElse("").get()
    val isWindows = providers.systemProperty("os.name").get().lowercase().contains("windows")
    val candidateBinaries = if (isWindows) listOf("code.cmd", "code.exe") else listOf("code")

    commandLine("code", "--extensionDevelopmentPath=$extensionPath", fixturesPath)

    // Preflight: fail fast with a useful message if `code` isn't on PATH,
    // rather than letting Exec emit a cryptic `exit code 127`. The CLI is
    // an optional VS Code install step ("Shell Command: Install 'code'
    // command in PATH") that surprises developers who installed VS Code
    // via the .app/.dmg without running that command palette action.
    doFirst {
        val found = pathEnv.split(File.pathSeparator).any { dir ->
            candidateBinaries.any { name -> File(dir, name).canExecute() }
        }
        if (!found) {
            throw GradleException(
                """
                |The `code` CLI is not on PATH, so this task cannot launch VS Code.
                |
                |How to fix: open VS Code, press Cmd+Shift+P (macOS) or Ctrl+Shift+P
                |(Linux/Windows), and run "Shell Command: Install 'code' command in PATH".
                |Open a new shell after it finishes so the updated PATH is picked up,
                |then re-run this task.
                |
                |Alternative that needs no PATH change: open lang/vscode-extension/
                |in VS Code and press F5 — that launches the Extension Development Host
                |directly from the IDE, equivalent to what this task does from the CLI.
                """.trimMargin(),
            )
        }
    }
}

val clean = tasks.named<Delete>("clean") {
    delete(layout.projectDirectory.dir("out"))
    delete(layout.projectDirectory.dir("node_modules"))
    delete(layout.projectDirectory.dir("server"))
    delete(layout.projectDirectory.file("syntaxes/xtc.tmLanguage.json"))
    delete(layout.projectDirectory.file("language-configuration.json"))
    delete(layout.projectDirectory.file("icons/xtc.png"))
    delete(layout.projectDirectory.file("icons/xtc-file.png"))
    delete(layout.projectDirectory.dir(".vscode-test"))
    delete(fileTree(layout.projectDirectory) { include("*.vsix") })
}
