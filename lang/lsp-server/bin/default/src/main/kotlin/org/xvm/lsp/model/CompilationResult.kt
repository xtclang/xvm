package org.xvm.lsp.model

/**
 * Immutable result of compiling an XTC source file.
 * This is what the adapter layer produces from the compiler.
 */
data class CompilationResult(
    val uri: String,
    val diagnostics: List<Diagnostic>,
    val symbols: List<SymbolInfo>,
    val success: Boolean,
) {
    companion object {
        fun success(
            uri: String,
            symbols: List<SymbolInfo>,
        ): CompilationResult = CompilationResult(uri, emptyList(), symbols.toList(), true)

        fun withDiagnostics(
            uri: String,
            diagnostics: List<Diagnostic>,
            symbols: List<SymbolInfo>,
        ): CompilationResult {
            val hasErrors = diagnostics.any { it.severity == Diagnostic.Severity.ERROR }
            return CompilationResult(uri, diagnostics.toList(), symbols.toList(), !hasErrors)
        }

        fun failure(
            uri: String,
            diagnostics: List<Diagnostic>,
        ): CompilationResult = CompilationResult(uri, diagnostics.toList(), emptyList(), false)
    }
}
