package org.xvm.lsp.adapter

import org.xvm.lsp.model.CompilationResult
import org.xvm.lsp.model.Location
import org.xvm.lsp.model.SymbolInfo
import org.xvm.lsp.util.WorkInProgress

/**
 * Stub implementation of the XTC compiler adapter.
 *
 * Minimal placeholder for future full compiler integration.
 * Returns null/empty for all operations. Use for testing LSP infrastructure
 * without tree-sitter or regex parsing dependencies.
 *
 * To use: `./gradlew :lang:lsp-server:fatJar -Plsp.adapter=compiler`
 *
 * @see AbstractXtcCompilerAdapter for shared functionality
 * @see TreeSitterAdapter for syntax-level features
 * @see MockXtcCompilerAdapter for regex-based testing
 */
@WorkInProgress("Awaiting full compiler integration")
class XtcCompilerAdapterStub : AbstractXtcCompilerAdapter() {
    override val displayName: String = "Compiler (stub)"

    override fun compile(
        uri: String,
        content: String,
    ): CompilationResult = CompilationResult.success(uri, emptyList())

    override fun findSymbolAt(
        uri: String,
        line: Int,
        column: Int,
    ): SymbolInfo? = null

    override fun getCompletions(
        uri: String,
        line: Int,
        column: Int,
    ): List<XtcCompilerAdapter.CompletionItem> = emptyList()

    override fun findDefinition(
        uri: String,
        line: Int,
        column: Int,
    ): Location? = null

    override fun findReferences(
        uri: String,
        line: Int,
        column: Int,
        includeDeclaration: Boolean,
    ): List<Location> = emptyList()
}
