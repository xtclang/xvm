import com.github.gradle.node.npm.task.NpmTask
import com.github.gradle.node.task.NodeTask

plugins {
    base
    alias(libs.plugins.lang.node.gradle)
}

node {
    version.set(libs.versions.lang.node.asProvider())
    download.set(true)
}

// Configuration to consume TextMate grammar from root project
val textMateGrammar by configurations.creating {
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
val copyTextMateGrammar by tasks.registering(Copy::class) {
    description = "Copy TextMate grammar from generated output"
    from(textMateGrammar) {
        include("xtc.tmLanguage.json")
    }
    into(layout.projectDirectory.dir("syntaxes"))
}

// Copy language configuration
val copyLanguageConfig by tasks.registering(Copy::class) {
    description = "Copy language configuration from generated output"
    from(textMateGrammar) {
        include("language-configuration.json")
    }
    into(layout.projectDirectory)
}

// Copy LSP server fat JAR (self-contained with all dependencies: LSP4J, tree-sitter, Logback)
val copyLspServer by tasks.registering(Copy::class) {
    description = "Copy LSP server fat JAR"
    dependsOn(project(":lsp-server").tasks.named("fatJar"))
    from(project(":lsp-server").tasks.named("fatJar"))
    into(layout.projectDirectory.dir("server"))
    rename { "lsp-server.jar" }
}

// Copy DAP server JAR (bundled for debugging support)
val copyDapServer by tasks.registering(Copy::class) {
    description = "Copy DAP server JAR"
    dependsOn(project(":dap-server").tasks.named("jar"))
    from(project(":dap-server").tasks.named("jar")) {
        include("*.jar")
    }
    into(layout.projectDirectory.dir("server"))
    rename { "dap-server.jar" }
}

// Copy LICENSE from repository root
val copyLicense by tasks.registering(Copy::class) {
    description = "Copy LICENSE from repository root"
    val compositeRoot = XdkPropertiesService.compositeRootDirectory(projectDir)
    from(File(compositeRoot, "LICENSE.md"))
    into(layout.projectDirectory)
}

// Generate the marketplace icon (xtc.png, 256x256) and the language file icon
// (xtc-file.png, 32x32) from doc/logo/x.jpg in the repository root. We derive
// PNGs rather than checking them in so the single JPEG source of truth in
// doc/logo/ stays canonical; the `sharp` devDependency handles the conversion.
val generateIcons by tasks.registering(NodeTask::class) {
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
val npmInstall by tasks.existing {
    mustRunAfter(copyLanguageConfig, copyTextMateGrammar, copyLicense)
}

// Compile TypeScript
val npmCompile by tasks.registering(NpmTask::class) {
    description = "Compile TypeScript"
    dependsOn(npmInstall)
    args.set(listOf("run", "compile"))

    inputs.dir(layout.projectDirectory.dir("src"))
    inputs.file(layout.projectDirectory.file("tsconfig.json"))
    outputs.dir(layout.projectDirectory.dir("out"))
}

// Package the extension
val packageExtension by tasks.registering(NpmTask::class) {
    description = "Package VS Code extension"
    dependsOn(npmCompile, copyTextMateGrammar, copyLanguageConfig, copyLspServer, copyDapServer, copyLicense, generateIcons)
    args.set(listOf("run", "package"))

    outputs.file(layout.projectDirectory.file("xtc-language-${project.version}.vsix"))
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
val testVscodeExtension by tasks.registering(NpmTask::class) {
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
val build by tasks.existing {
    dependsOn(packageExtension)
}

// Assemble prepares all resources without packaging
val assemble by tasks.existing {
    dependsOn(copyTextMateGrammar, copyLanguageConfig, copyLspServer, copyDapServer, copyLicense, npmCompile, generateIcons)
}

// Launch VS Code with extension loaded for testing
val runCode by tasks.registering(Exec::class) {
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

val clean by tasks.existing(Delete::class) {
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
