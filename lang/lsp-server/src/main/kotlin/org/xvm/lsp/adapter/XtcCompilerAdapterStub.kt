package org.xvm.lsp.adapter

import org.slf4j.LoggerFactory
import org.xvm.lsp.model.CompilationResult
import org.xvm.lsp.model.Location
import org.xvm.lsp.model.SymbolInfo

/**
 * Stub implementation of the XTC compiler adapter.
 *
 * This adapter serves as a placeholder for the future full compiler integration.
 * It implements only the core required methods with minimal functionality,
 * while all other methods use the default interface implementations that
 * log "not yet implemented" warnings.
 *
 * Use this adapter to:
 * - Test the LSP server infrastructure without tree-sitter native dependencies
 * - Verify that all LSP methods are properly wired up (all calls get logged)
 * - Prepare for future compiler integration
 *
 * To use: `./gradlew :lang:lsp-server:fatJar -Plsp.adapter=compiler`
 *
 * TODO LSP: Replace this stub with a real implementation that uses:
 * - Phase 1-3: Lexer/Parser for accurate parsing
 * - Phase 4: NameResolver for symbol resolution
 * - Phase 5-7: Full semantic analysis for type-aware features
 *
 * @see XtcCompilerAdapter for the full interface specification
 * @see TreeSitterAdapter for syntax-level features (~70% LSP coverage)
 * @see MockXtcCompilerAdapter for regex-based testing
 */
class XtcCompilerAdapterStub : XtcCompilerAdapter {
    override val displayName: String = "Compiler (stub)"

    private val logPrefix = "[$displayName]"

    companion object {
        private val logger = LoggerFactory.getLogger(XtcCompilerAdapterStub::class.java)
    }

    override fun healthCheck(): Boolean {
        logger.info("$logPrefix healthCheck() -> true (stub)")
        return true
    }

    override fun compile(
        uri: String,
        content: String,
    ): CompilationResult {
        logger.info("$logPrefix compile({}, {} bytes) -> empty result (stub)", uri, content.length)
        // Return empty result - no parsing, no symbols, no diagnostics
        return CompilationResult.success(uri, emptyList())
    }

    override fun findSymbolAt(
        uri: String,
        line: Int,
        column: Int,
    ): SymbolInfo? {
        logger.info("$logPrefix findSymbolAt({}, {}:{}) -> null (stub)", uri, line, column)
        return null
    }

    override fun getHoverInfo(
        uri: String,
        line: Int,
        column: Int,
    ): String? {
        logger.info("$logPrefix getHoverInfo({}, {}:{}) -> null (stub)", uri, line, column)
        return null
    }

    override fun getCompletions(
        uri: String,
        line: Int,
        column: Int,
    ): List<XtcCompilerAdapter.CompletionItem> {
        logger.info("$logPrefix getCompletions({}, {}:{}) -> empty (stub)", uri, line, column)
        return emptyList()
    }

    override fun findDefinition(
        uri: String,
        line: Int,
        column: Int,
    ): Location? {
        logger.info("$logPrefix findDefinition({}, {}:{}) -> null (stub)", uri, line, column)
        return null
    }

    override fun findReferences(
        uri: String,
        line: Int,
        column: Int,
        includeDeclaration: Boolean,
    ): List<Location> {
        logger.info(
            "$logPrefix findReferences({}, {}:{}, includeDecl={}) -> empty (stub)",
            uri,
            line,
            column,
            includeDeclaration,
        )
        return emptyList()
    }

    // All other methods (getDocumentHighlights, getFoldingRanges, getSignatureHelp,
    // rename, getCodeActions, getSemanticTokens, getInlayHints, formatDocument, etc.)
    // automatically use the default interface implementations which log
    // "[Compiler (stub)] methodName not yet implemented (requires compiler)"
}
