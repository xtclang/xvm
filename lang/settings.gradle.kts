pluginManagement {
    includeBuild("../build-logic/settings-plugins")
    includeBuild("../build-logic/common-plugins")
}

plugins {
    id("org.xtclang.build.common")
}

rootProject.name = "xtc-lang"

// Subprojects
include("dsl")
include("tree-sitter")
include("lsp-server")
include("debug-adapter")
include("intellij-plugin")
include("vscode-extension")
