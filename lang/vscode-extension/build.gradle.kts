plugins {
    base
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
    textMateGrammar(project(path = ":", configuration = "textMateElements"))
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

// Copy icon from IntelliJ plugin
val copyIcon by tasks.registering(Copy::class) {
    description = "Copy XTC icon"
    from(project(":intellij-plugin").file("src/main/resources/icons"))
    into(layout.projectDirectory.dir("icons"))
}

// Copy LSP server JAR
val copyLspServer by tasks.registering(Copy::class) {
    description = "Copy LSP server JAR"
    dependsOn(project(":lsp-server").tasks.named("jar"))
    from(project(":lsp-server").tasks.named("jar"))
    into(layout.projectDirectory.dir("server"))
    rename { "lsp-server.jar" }
}

// Run npm install
val npmInstall by tasks.registering(Exec::class) {
    description = "Install npm dependencies"
    workingDir = layout.projectDirectory.asFile
    commandLine("npm", "install")

    // Must run after copy tasks that write to the project directory
    mustRunAfter(copyLanguageConfig, copyTextMateGrammar, copyIcon)

    inputs.file(layout.projectDirectory.file("package.json"))
    outputs.dir(layout.projectDirectory.dir("node_modules"))
}

// Compile TypeScript
val npmCompile by tasks.registering(Exec::class) {
    description = "Compile TypeScript"
    dependsOn(npmInstall)
    workingDir = layout.projectDirectory.asFile
    commandLine("npm", "run", "compile")

    inputs.dir(layout.projectDirectory.dir("src"))
    inputs.file(layout.projectDirectory.file("tsconfig.json"))
    outputs.dir(layout.projectDirectory.dir("out"))
}

// Package the extension
val packageExtension by tasks.registering(Exec::class) {
    description = "Package VS Code extension"
    dependsOn(npmCompile, copyTextMateGrammar, copyLanguageConfig, copyIcon, copyLspServer)
    workingDir = layout.projectDirectory.asFile
    commandLine("npm", "run", "package")

    outputs.file(layout.projectDirectory.file("xtc-language-${project.version}.vsix"))
}

// Main build task - configure the existing task from base plugin
val build by tasks.existing {
    dependsOn(packageExtension)
}

// Assemble prepares all resources without packaging
val assemble by tasks.existing {
    dependsOn(copyTextMateGrammar, copyLanguageConfig, copyIcon, copyLspServer, npmCompile)
}

// Launch VS Code with extension loaded for testing (like IntelliJ's runIde)
val runCode by tasks.registering(Exec::class) {
    group = "run"
    description = "Launch VS Code with the extension loaded for testing"
    dependsOn(assemble)

    val extensionPath = layout.projectDirectory.asFile.absolutePath
    commandLine("code", "--extensionDevelopmentPath=$extensionPath")
}

val clean by tasks.existing(Delete::class) {
    delete(layout.projectDirectory.dir("out"))
    delete(layout.projectDirectory.dir("node_modules"))
    delete(layout.projectDirectory.dir("server"))
    delete(layout.projectDirectory.file("syntaxes/xtc.tmLanguage.json"))
    delete(layout.projectDirectory.file("language-configuration.json"))
    delete(fileTree(layout.projectDirectory) { include("*.vsix") })
}
