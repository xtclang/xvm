package org.xvm.lsp.protocol

/** The `workspace/configuration` sections that the language server asks its client for. */
object ConfigurationSections {
    /** Whether the server's lenses show each declaration's references: `{ "references": Boolean }`. */
    const val CODE_LENS = "xtc.codeLens"

    /** The compiler's source modules, build models and libraries, which a client's settings hold under `xtc` and `compiler`. */
    const val COMPILER = "xtc.compiler"

    /** The editor's formatting options: indent, continuation indent and tab sizes, spaces or tabs, and line width. */
    const val FORMATTING = "xtc.formatting"
}

/** The system properties that a client starts the language server's JVM with, as `-D` options, and the server reads. */
object ServerProperties {
    /** Where the server writes its logs, a session directory for each process. */
    const val LOGS_DIRECTORY = "xtc.logs.directory"

    /** Where the server writes its protocol and execution traces; its session's log directory without it. */
    const val TRACE_DIRECTORY = "xtc.trace.directory"

    /** Whether an XDK source opened in an editor is compiled with its whole library, for navigation in it. */
    const val COMPILE_LIBRARIES = "xtc.lsp.compileLibraries"

    /** Whether a workspace module named as a bundled XDK library replaces it. */
    const val REPLACE_LIBRARIES = "xtc.lsp.replaceLibraries"

    /** Whether the server offers semantic tokens. */
    const val SEMANTIC_TOKENS = "xtc.lsp.semanticTokens"

    /** A test seam: the file whose reference searches wait until they are canceled, which only the IntelliJ playbook sets. */
    const val HOLD_REFERENCES = "xtc.lsp.test.holdReferences"
}
