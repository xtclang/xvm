package org.xvm.lsp.protocol

import java.net.URI
import java.nio.file.Path

/**
 * `xtc/build` parameters: a module name or root URI; or when [module] is absent, the source modules of the workspace
 * folder [folder], a URI, or every source module without one. With [rebuild], a module whose sources and dependencies
 * have not changed since its last compile is compiled again.
 */
data class BuildParams(
    val module: String? = null,
    val rebuild: Boolean? = null,
    val folder: String? = null,
)

/** One module's outcome in an embedded build. */
data class BuiltModule(
    val name: String = "",
    val uri: String = "",
    val succeeded: Boolean = false,
    /** A dependency failed, so this module was not compiled. */
    val skipped: Boolean = false,
    /** The written .xtc file, when the module compiled. */
    val artifact: String? = null,
    /** An earlier compile of exactly these sources and dependency revisions supplied the artifact. */
    val reused: Boolean = false,
    /** SHA-256 identity of the written artifact. */
    val revision: String? = null,
)

/** `xtc/build` result. Diagnostics are grouped by document, in the shape of `publishDiagnostics`. */
data class BuildResponse(
    val succeeded: Boolean = false,
    val cancelled: Boolean = false,
    val modules: List<BuiltModule> = emptyList(),
    val diagnostics: List<DocumentDiagnostics> = emptyList(),
    val outputDirectory: String? = null,
) {
    /**
     * The build as a console shows it: a line for each module's outcome, `  ✓ module -> artifact`, `  - module (not
     * built: a dependency failed)` or `  ✗ module`; then each diagnostic as `file:line:column: severity: message [code]`,
     * one-based, with the file's path where its document's URI names a local file; and `Build cancelled.` when it was.
     */
    fun summary(): List<String> =
        buildList {
            modules.forEach { module ->
                add(
                    when {
                        module.succeeded -> "  ✓ ${module.name}${module.artifact?.let { " -> $it" }.orEmpty()}"
                        module.skipped -> "  - ${module.name} (not built: a dependency failed)"
                        else -> "  ✗ ${module.name}"
                    },
                )
            }
            diagnostics.forEach { document ->
                val file = runCatching { Path.of(URI(document.uri)).toString() }.getOrDefault(document.uri)
                document.diagnostics.forEach { diagnostic ->
                    val start = diagnostic.range.start
                    val code = diagnostic.code?.let { " [$it]" }.orEmpty()
                    add("$file:${start.line + 1}:${start.character + 1}: ${severity(diagnostic.severity)}: ${diagnostic.message}$code")
                }
            }
            if (cancelled) add("Build cancelled.")
        }
}

private fun severity(severity: Int?): String =
    when (severity) {
        BuildDiagnostic.WARNING -> "warning"
        BuildDiagnostic.INFORMATION -> "info"
        BuildDiagnostic.HINT -> "hint"
        else -> "error"
    }

/**
 * A document's build diagnostics, as `publishDiagnostics` carries them. These mirror LSP's shapes rather than reuse
 * lsp4j's, whose `Diagnostic` differs between the lsp4j that the server bundles and LSP4IJ's, which the plugin has.
 */
data class DocumentDiagnostics(
    val uri: String = "",
    val diagnostics: List<BuildDiagnostic> = emptyList(),
)

/**
 * A compiler diagnostic: zero-based [range], LSP [severity] (1 error to 4 hint), its [code] and [source], plain-text
 * [message], LSP [tags] (1 unnecessary, 2 deprecated), and [relatedInformation], which says where in another document
 * a diagnostic that this document is published with is.
 */
data class BuildDiagnostic(
    val range: BuildRange = BuildRange(),
    val severity: Int? = null,
    val code: String? = null,
    val source: String? = null,
    val message: String = "",
    val tags: List<Int>? = null,
    val relatedInformation: List<BuildRelatedInformation>? = null,
) {
    companion object {
        /** LSP's severities, as [severity] carries them. */
        const val ERROR = 1
        const val WARNING = 2
        const val INFORMATION = 3
        const val HINT = 4
    }
}

data class BuildRange(
    val start: BuildPosition = BuildPosition(),
    val end: BuildPosition = BuildPosition(),
)

data class BuildPosition(
    val line: Int = 0,
    val character: Int = 0,
)

/** Where a diagnostic is, when it is published with another document, and its message there. */
data class BuildRelatedInformation(
    val location: BuildLocation = BuildLocation(),
    val message: String = "",
)

data class BuildLocation(
    val uri: String = "",
    val range: BuildRange = BuildRange(),
)
