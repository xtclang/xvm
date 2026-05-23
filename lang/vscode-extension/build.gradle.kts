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
    commandLine("code", "--extensionDevelopmentPath=$extensionPath", fixturesPath)
}

val clean by tasks.existing(Delete::class) {
    delete(layout.projectDirectory.dir("out"))
    delete(layout.projectDirectory.dir("node_modules"))
    delete(layout.projectDirectory.dir("server"))
    delete(layout.projectDirectory.file("syntaxes/xtc.tmLanguage.json"))
    delete(layout.projectDirectory.file("language-configuration.json"))
    delete(layout.projectDirectory.file("icons/xtc.png"))
    delete(layout.projectDirectory.file("icons/xtc-file.png"))
    delete(fileTree(layout.projectDirectory) { include("*.vsix") })
}
