package org.xvm.lsp.adapter

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.xvm.lsp.adapter.XtcLanguageConstants.toHoverMarkdown
import org.xvm.lsp.model.Diagnostic
import org.xvm.lsp.model.Location
import org.xvm.lsp.model.SymbolInfo

/**
 * Abstract base class for XTC compiler adapters.
 *
 * Provides common functionality shared across all adapter implementations:
 * - Per-class logging with consistent `[displayName]` prefix formatting
 * - Default "not yet implemented" stubs for all optional LSP features
 * - Default [getHoverInfo] implementation using [findSymbolAt]
 * - Utility method for position-in-range checking
 * - No-op [java.io.Closeable] implementation (override in subclasses that need cleanup)
 *
 * Concrete adapters override only the methods they actually implement.
 * All unimplemented methods log the full input parameters and return null/empty,
 * so the log trace shows exactly what the IDE requested even when the feature
 * is not yet available.
 *
 * @see [MockXtcCompilerAdapter] for regex-based testing adapter
 * @see [TreeSitterAdapter] for syntax-aware adapter
 * @see [XtcCompilerAdapterStub] for placeholder adapter
 */
@Suppress("LoggingSimilarMessage")
abstract class AbstractXtcCompilerAdapter : XtcCompilerAdapter {
    /**
     * Logger instance for this adapter, using the concrete class name.
     * Lazily initialized to use the actual subclass type.
     */
    protected val logger: Logger by lazy {
        LoggerFactory.getLogger(this::class.java)
    }

    /**
     * Logging prefix derived from [displayName], e.g., "[Mock]" or "[TreeSitter]".
     */
    protected val logPrefix: String get() = "[$displayName]"

    // ========================================================================
    // Default implementations -- hover (uses findSymbolAt)
    // ========================================================================

    /**
     * Default hover implementation that finds the symbol at position and formats it.
     *
     * Subclasses can override for custom behavior or additional logging.
     */
    override fun getHoverInfo(
        uri: String,
        line: Int,
        column: Int,
    ): String? {
        logger.info("$logPrefix getHoverInfo: uri={}, line={}, column={}", uri, line, column)
        val symbol = findSymbolAt(uri, line, column)
        if (symbol == null) {
            logger.info("$logPrefix getHoverInfo: no symbol at position")
            return null
        }
        logger.info("$logPrefix getHoverInfo: found symbol '{}' ({})", symbol.name, symbol.kind)
        return symbol.toHoverMarkdown()
    }

    // ========================================================================
    // Default implementations -- workspace lifecycle
    // ========================================================================

    override fun initializeWorkspace(
        workspaceFolders: List<String>,
        progressReporter: ((String, Int) -> Unit)?,
    ) {
        logger.info("$logPrefix initializeWorkspace: {} folders: {}", workspaceFolders.size, workspaceFolders)
    }

    override fun didChangeWatchedFile(
        uri: String,
        changeType: Int,
    ) {
        logger.info("$logPrefix didChangeWatchedFile: uri={}, changeType={}", uri, changeType)
    }

    override fun closeDocument(uri: String) {
        logger.info("$logPrefix closeDocument: uri={}", uri)
    }

    // ========================================================================
    // Default implementations -- tree-sitter capable features
    // ========================================================================

    override fun getDocumentHighlights(
        uri: String,
        line: Int,
        column: Int,
    ): List<XtcCompilerAdapter.DocumentHighlight> {
        logger.warn("$logPrefix [NOT IMPLEMENTED] getDocumentHighlights: uri={}, line={}, column={}", uri, line, column)
        return emptyList()
    }

    override fun getSelectionRanges(
        uri: String,
        positions: List<XtcCompilerAdapter.Position>,
    ): List<XtcCompilerAdapter.SelectionRange> {
        logger.warn("$logPrefix [NOT IMPLEMENTED] getSelectionRanges: uri={}, positions={}", uri, positions)
        return emptyList()
    }

    override fun getFoldingRanges(uri: String): List<XtcCompilerAdapter.FoldingRange> {
        logger.warn("$logPrefix [NOT IMPLEMENTED] getFoldingRanges: uri={}", uri)
        return emptyList()
    }

    override fun getDocumentLinks(
        uri: String,
        content: String,
    ): List<XtcCompilerAdapter.DocumentLink> {
        logger.warn("$logPrefix [NOT IMPLEMENTED] getDocumentLinks: uri={}, content={} bytes", uri, content.length)
        return emptyList()
    }

    // ========================================================================
    // Default implementations -- semantic features (require full compiler)
    // ========================================================================

    override fun getSignatureHelp(
        uri: String,
        line: Int,
        column: Int,
    ): XtcCompilerAdapter.SignatureHelp? {
        logger.warn("$logPrefix [NOT IMPLEMENTED] getSignatureHelp: uri={}, line={}, column={}", uri, line, column)
        return null
    }

    override fun prepareRename(
        uri: String,
        line: Int,
        column: Int,
    ): XtcCompilerAdapter.PrepareRenameResult? {
        logger.warn("$logPrefix [NOT IMPLEMENTED] prepareRename: uri={}, line={}, column={}", uri, line, column)
        return null
    }

    override fun rename(
        uri: String,
        line: Int,
        column: Int,
        newName: String,
    ): XtcCompilerAdapter.WorkspaceEdit? {
        logger.warn(
            "$logPrefix [NOT IMPLEMENTED] rename: uri={}, line={}, column={}, newName='{}'",
            uri,
            line,
            column,
            newName,
        )
        return null
    }

    override fun getCodeActions(
        uri: String,
        range: XtcCompilerAdapter.Range,
        diagnostics: List<Diagnostic>,
    ): List<XtcCompilerAdapter.CodeAction> {
        logger.warn(
            "$logPrefix [NOT IMPLEMENTED] getCodeActions: uri={}, range={}:{}-{}:{}, diagnostics={}",
            uri,
            range.start.line,
            range.start.column,
            range.end.line,
            range.end.column,
            diagnostics.size,
        )
        return emptyList()
    }

    override fun getSemanticTokens(uri: String): XtcCompilerAdapter.SemanticTokens? {
        logger.warn("$logPrefix [NOT IMPLEMENTED] getSemanticTokens: uri={}", uri)
        return null
    }

    override fun getInlayHints(
        uri: String,
        range: XtcCompilerAdapter.Range,
    ): List<XtcCompilerAdapter.InlayHint> {
        logger.warn(
            "$logPrefix [NOT IMPLEMENTED] getInlayHints: uri={}, range={}:{}-{}:{}",
            uri,
            range.start.line,
            range.start.column,
            range.end.line,
            range.end.column,
        )
        return emptyList()
    }

    override fun formatDocument(
        uri: String,
        content: String,
        options: XtcCompilerAdapter.FormattingOptions,
    ): List<XtcCompilerAdapter.TextEdit> = formatContent(content, options, null)

    override fun formatRange(
        uri: String,
        content: String,
        range: XtcCompilerAdapter.Range,
        options: XtcCompilerAdapter.FormattingOptions,
    ): List<XtcCompilerAdapter.TextEdit> = formatContent(content, options, range)

    /**
     * Basic formatting: trailing whitespace removal and final newline insertion.
     * If [range] is non-null, only lines within that range are formatted.
     *
     * Shared by all adapters -- override [formatDocument]/[formatRange] in subclasses
     * that need different formatting logic.
     */
    private fun formatContent(
        content: String,
        options: XtcCompilerAdapter.FormattingOptions,
        range: XtcCompilerAdapter.Range?,
    ): List<XtcCompilerAdapter.TextEdit> =
        buildList {
            val lines = content.split("\n")
            val startLine = range?.start?.line ?: 0
            val endLine = range?.end?.line ?: (lines.size - 1)

            // Trailing whitespace removal
            for (i in startLine..minOf(endLine, lines.size - 1)) {
                val line = lines[i]
                val trimmed = line.trimEnd()
                if (trimmed.length < line.length && (options.trimTrailingWhitespace || range == null)) {
                    add(
                        XtcCompilerAdapter.TextEdit(
                            range =
                                XtcCompilerAdapter.Range(
                                    start = XtcCompilerAdapter.Position(i, trimmed.length),
                                    end = XtcCompilerAdapter.Position(i, line.length),
                                ),
                            newText = "",
                        ),
                    )
                }
            }

            // Insert final newline if requested and missing (only for full-document format)
            if (range == null && options.insertFinalNewline && content.isNotEmpty() && !content.endsWith("\n")) {
                val lastLine = lines.size - 1
                val lastCol = lines[lastLine].length
                add(
                    XtcCompilerAdapter.TextEdit(
                        range =
                            XtcCompilerAdapter.Range(
                                start = XtcCompilerAdapter.Position(lastLine, lastCol),
                                end = XtcCompilerAdapter.Position(lastLine, lastCol),
                            ),
                        newText = "\n",
                    ),
                )
            }
        }.also {
            logger.info("$logPrefix format -> {} edits", it.size)
        }

    override fun findWorkspaceSymbols(query: String): List<SymbolInfo> {
        logger.warn("$logPrefix [NOT IMPLEMENTED] findWorkspaceSymbols: query='{}'", query)
        return emptyList()
    }

    // ========================================================================
    // Default implementations -- planned features
    // ========================================================================

    override fun findDeclaration(
        uri: String,
        line: Int,
        column: Int,
    ): Location? {
        logger.warn("$logPrefix [NOT IMPLEMENTED] findDeclaration: uri={}, line={}, column={}", uri, line, column)
        return null
    }

    override fun findTypeDefinition(
        uri: String,
        line: Int,
        column: Int,
    ): Location? {
        logger.warn("$logPrefix [NOT IMPLEMENTED] findTypeDefinition: uri={}, line={}, column={}", uri, line, column)
        return null
    }

    override fun findImplementation(
        uri: String,
        line: Int,
        column: Int,
    ): List<Location> {
        logger.warn(
            "$logPrefix [NOT IMPLEMENTED] findImplementation: uri={}, line={}, column={}",
            uri,
            line,
            column,
        )
        return emptyList()
    }

    override fun prepareTypeHierarchy(
        uri: String,
        line: Int,
        column: Int,
    ): List<XtcCompilerAdapter.TypeHierarchyItem> {
        logger.warn(
            "$logPrefix [NOT IMPLEMENTED] prepareTypeHierarchy: uri={}, line={}, column={}",
            uri,
            line,
            column,
        )
        return emptyList()
    }

    override fun getSupertypes(item: XtcCompilerAdapter.TypeHierarchyItem): List<XtcCompilerAdapter.TypeHierarchyItem> {
        logger.warn("$logPrefix [NOT IMPLEMENTED] getSupertypes: item={}", item.name)
        return emptyList()
    }

    override fun getSubtypes(item: XtcCompilerAdapter.TypeHierarchyItem): List<XtcCompilerAdapter.TypeHierarchyItem> {
        logger.warn("$logPrefix [NOT IMPLEMENTED] getSubtypes: item={}", item.name)
        return emptyList()
    }

    override fun prepareCallHierarchy(
        uri: String,
        line: Int,
        column: Int,
    ): List<XtcCompilerAdapter.CallHierarchyItem> {
        logger.warn(
            "$logPrefix [NOT IMPLEMENTED] prepareCallHierarchy: uri={}, line={}, column={}",
            uri,
            line,
            column,
        )
        return emptyList()
    }

    override fun getIncomingCalls(item: XtcCompilerAdapter.CallHierarchyItem): List<XtcCompilerAdapter.CallHierarchyIncomingCall> {
        logger.warn("$logPrefix [NOT IMPLEMENTED] getIncomingCalls: item={}", item.name)
        return emptyList()
    }

    override fun getOutgoingCalls(item: XtcCompilerAdapter.CallHierarchyItem): List<XtcCompilerAdapter.CallHierarchyOutgoingCall> {
        logger.warn("$logPrefix [NOT IMPLEMENTED] getOutgoingCalls: item={}", item.name)
        return emptyList()
    }

    override fun getCodeLenses(uri: String): List<XtcCompilerAdapter.CodeLens> {
        logger.warn("$logPrefix [NOT IMPLEMENTED] getCodeLenses: uri={}", uri)
        return emptyList()
    }

    override fun resolveCodeLens(lens: XtcCompilerAdapter.CodeLens): XtcCompilerAdapter.CodeLens {
        logger.warn(
            "$logPrefix [NOT IMPLEMENTED] resolveCodeLens: lens range={}:{}-{}:{}",
            lens.range.start.line,
            lens.range.start.column,
            lens.range.end.line,
            lens.range.end.column,
        )
        return lens
    }

    override fun onTypeFormatting(
        uri: String,
        line: Int,
        column: Int,
        ch: String,
        options: XtcCompilerAdapter.FormattingOptions,
    ): List<XtcCompilerAdapter.TextEdit> {
        logger.warn("$logPrefix [NOT IMPLEMENTED] onTypeFormatting: uri={}, line={}, column={}, ch='{}'", uri, line, column, ch)
        return emptyList()
    }

    override fun getLinkedEditingRanges(
        uri: String,
        line: Int,
        column: Int,
    ): XtcCompilerAdapter.LinkedEditingRanges? {
        logger.warn("$logPrefix [NOT IMPLEMENTED] getLinkedEditingRanges: uri={}, line={}, column={}", uri, line, column)
        return null
    }

    // ========================================================================
    // Utility
    // ========================================================================

    /**
     * Check if a position (line, column) falls within a location's range.
     *
     * @param line 0-based line number
     * @param column 0-based column number
     * @return true if the position is within this location's bounds
     */
    protected fun Location.contains(
        line: Int,
        column: Int,
    ): Boolean {
        if (line !in startLine..endLine) return false
        if (line == startLine && column < startColumn) return false
        if (line == endLine && column > endColumn) return false
        return true
    }
}
