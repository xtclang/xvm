package org.xvm.lsp.adapter

import org.slf4j.LoggerFactory
import org.xvm.lsp.model.CompilationResult
import org.xvm.lsp.model.Diagnostic
import org.xvm.lsp.model.Location
import org.xvm.lsp.model.SymbolInfo

/**
 * Interface for adapting XTC compiler operations into clean, immutable results.
 *
 * The adapter layer isolates the LSP server from the compiler's mutable internals.
 * All results are immutable and thread-safe.
 *
 * ## Available Implementations
 *
 * | Adapter | Backend | Use Case |
 * |---------|---------|----------|
 * | [MockXtcCompilerAdapter] | Regex | Testing and fallback |
 * | [TreeSitterAdapter] | Tree-sitter | Syntax-aware (~70% LSP features) |
 * | XtcCompilerAdapterFull | Compiler | (future) Full semantic features |
 *
 * ## Backend Selection
 *
 * Select at build time: `./gradlew :lang:lsp-server:build -Plsp.adapter=treesitter`
 *
 * - `mock` (default): Regex-based, no native dependencies
 * - `treesitter`: Syntax-aware parsing, requires native library
 *
 * @see TreeSitterAdapter for syntax-level intelligence
 * @see MockXtcCompilerAdapter for testing
 */
interface XtcCompilerAdapter {
    companion object {
        private val logger = LoggerFactory.getLogger(XtcCompilerAdapter::class.java)
    }

    /**
     * Human-readable name of this adapter for display in logs and UI.
     * Examples: "Mock", "TreeSitter", "Compiler"
     */
    val displayName: String
        get() = this::class.simpleName ?: "Unknown"

    /**
     * Perform a health check to verify the adapter is working correctly.
     *
     * For adapters using native code (e.g., TreeSitterAdapter), this verifies
     * that the native library is loaded and functional.
     *
     * @return true if the adapter is healthy, false otherwise
     */
    fun healthCheck(): Boolean = true

    // ========================================================================
    // Core LSP Features (implemented by all adapters)
    // ========================================================================

    /**
     * Compile a source file and return the result.
     *
     * LSP: Triggered by textDocument/didOpen and textDocument/didChange.
     *
     * @param uri     the document URI
     * @param content the source code content
     * @return compilation result with diagnostics and symbols
     */
    fun compile(
        uri: String,
        content: String,
    ): CompilationResult

    /**
     * Find the symbol at a specific position.
     *
     * Used internally by hover, definition, and references.
     *
     * @param uri    the document URI
     * @param line   0-based line number
     * @param column 0-based column number
     * @return the symbol at that position, if any
     */
    fun findSymbolAt(
        uri: String,
        line: Int,
        column: Int,
    ): SymbolInfo?

    /**
     * Get hover information for a position.
     *
     * LSP: textDocument/hover
     *
     * @param uri    the document URI
     * @param line   0-based line number
     * @param column 0-based column number
     * @return hover text (Markdown), if available
     */
    fun getHoverInfo(
        uri: String,
        line: Int,
        column: Int,
    ): String?

    /**
     * Get completion suggestions at a position.
     *
     * LSP: textDocument/completion
     *
     * @param uri    the document URI
     * @param line   0-based line number
     * @param column 0-based column number
     * @return list of completion items
     */
    fun getCompletions(
        uri: String,
        line: Int,
        column: Int,
    ): List<CompletionItem>

    /**
     * Find the definition of the symbol at a position.
     *
     * LSP: textDocument/definition
     *
     * @param uri    the document URI
     * @param line   0-based line number
     * @param column 0-based column number
     * @return location of the definition, if found
     */
    fun findDefinition(
        uri: String,
        line: Int,
        column: Int,
    ): Location?

    /**
     * Find all references to the symbol at a position.
     *
     * LSP: textDocument/references
     *
     * @param uri                the document URI
     * @param line               0-based line number
     * @param column             0-based column number
     * @param includeDeclaration whether to include the declaration itself
     * @return list of reference locations
     */
    fun findReferences(
        uri: String,
        line: Int,
        column: Int,
        includeDeclaration: Boolean,
    ): List<Location>

    // ========================================================================
    // Tree-sitter capable features (syntax-based, no semantic analysis)
    // ========================================================================

    /**
     * Get document highlights for a symbol at a position.
     *
     * Highlights all occurrences of the symbol in the same document.
     * Tree-sitter can implement this via text matching on identifier names.
     *
     * LSP: textDocument/documentHighlight
     *
     * @param uri    the document URI
     * @param line   0-based line number
     * @param column 0-based column number
     * @return list of highlight locations with their kind
     */
    fun getDocumentHighlights(
        uri: String,
        line: Int,
        column: Int,
    ): List<DocumentHighlight> {
        logger.info("[{}] getDocumentHighlights not yet implemented", displayName)
        return emptyList()
    }

    /**
     * Get selection ranges for positions (smart selection expansion).
     *
     * Returns nested ranges based on AST structure for expand/shrink selection.
     * Tree-sitter can implement this directly from parse tree structure.
     *
     * LSP: textDocument/selectionRange
     *
     * @param uri       the document URI
     * @param positions list of positions to get selection ranges for
     * @return list of selection ranges (one per input position)
     */
    fun getSelectionRanges(
        uri: String,
        positions: List<Position>,
    ): List<SelectionRange> {
        logger.info("[{}] getSelectionRanges not yet implemented", displayName)
        return emptyList()
    }

    /**
     * Get folding ranges for a document.
     *
     * Returns regions that can be collapsed (classes, methods, blocks, imports).
     * Tree-sitter can implement this from AST node boundaries.
     *
     * LSP: textDocument/foldingRange
     *
     * @param uri the document URI
     * @return list of folding ranges
     */
    fun getFoldingRanges(uri: String): List<FoldingRange> {
        logger.info("[{}] getFoldingRanges not yet implemented", displayName)
        return emptyList()
    }

    /**
     * Get document links (clickable paths in imports, etc.).
     *
     * Tree-sitter can extract import statements and return them as links.
     *
     * LSP: textDocument/documentLink
     *
     * @param uri     the document URI
     * @param content the source code content
     * @return list of document links
     */
    fun getDocumentLinks(
        uri: String,
        content: String,
    ): List<DocumentLink> {
        logger.info("[{}] getDocumentLinks not yet implemented", displayName)
        return emptyList()
    }

    // ========================================================================
    // Semantic features (require full compiler)
    // ========================================================================

    /**
     * Get signature help for a function call at a position.
     *
     * Shows parameter info when typing function arguments.
     * Requires resolved method signatures from compiler.
     *
     * LSP: textDocument/signatureHelp
     *
     * @param uri    the document URI
     * @param line   0-based line number
     * @param column 0-based column number
     * @return signature help info, if available
     */
    fun getSignatureHelp(
        uri: String,
        line: Int,
        column: Int,
    ): SignatureHelp? {
        logger.info("[{}] getSignatureHelp not yet implemented (requires compiler)", displayName)
        return null
    }

    /**
     * Prepare rename operation - check if rename is valid at position.
     *
     * LSP: textDocument/prepareRename
     *
     * @param uri    the document URI
     * @param line   0-based line number
     * @param column 0-based column number
     * @return rename range and placeholder text, or null if rename not allowed
     */
    fun prepareRename(
        uri: String,
        line: Int,
        column: Int,
    ): PrepareRenameResult? {
        logger.info("[{}] prepareRename not yet implemented (requires compiler)", displayName)
        return null
    }

    /**
     * Perform rename operation.
     *
     * Renames a symbol across all files in the workspace.
     * Requires full semantic analysis from compiler.
     *
     * LSP: textDocument/rename
     *
     * @param uri     the document URI
     * @param line    0-based line number
     * @param column  0-based column number
     * @param newName the new name for the symbol
     * @return workspace edit with all changes, or null if rename failed
     */
    fun rename(
        uri: String,
        line: Int,
        column: Int,
        newName: String,
    ): WorkspaceEdit? {
        logger.info("[{}] rename not yet implemented (requires compiler)", displayName)
        return null
    }

    /**
     * Get code actions for a range (quick fixes, refactorings).
     *
     * Returns available actions based on diagnostics and context.
     * Requires semantic analysis for meaningful suggestions.
     *
     * LSP: textDocument/codeAction
     *
     * @param uri         the document URI
     * @param range       the range to get actions for
     * @param diagnostics diagnostics in the range
     * @return list of available code actions
     */
    fun getCodeActions(
        uri: String,
        range: Range,
        diagnostics: List<Diagnostic>,
    ): List<CodeAction> {
        logger.info("[{}] getCodeActions not yet implemented (requires compiler)", displayName)
        return emptyList()
    }

    /**
     * Get semantic tokens for enhanced syntax highlighting.
     *
     * Returns token classifications based on semantic analysis.
     * Requires type resolution from compiler.
     *
     * LSP: textDocument/semanticTokens/full
     *
     * @param uri the document URI
     * @return semantic tokens data
     */
    fun getSemanticTokens(uri: String): SemanticTokens? {
        logger.info("[{}] getSemanticTokens not yet implemented (requires compiler)", displayName)
        return null
    }

    /**
     * Get inlay hints (inline type annotations, parameter names).
     *
     * Shows inferred types and parameter names inline.
     * Requires type inference from compiler.
     *
     * LSP: textDocument/inlayHint
     *
     * @param uri   the document URI
     * @param range the range to get hints for
     * @return list of inlay hints
     */
    fun getInlayHints(
        uri: String,
        range: Range,
    ): List<InlayHint> {
        logger.info("[{}] getInlayHints not yet implemented (requires compiler)", displayName)
        return emptyList()
    }

    /**
     * Format an entire document.
     *
     * LSP: textDocument/formatting
     *
     * @param uri     the document URI
     * @param content the source code content
     * @param options formatting options (tab size, etc.)
     * @return list of text edits to apply
     */
    fun formatDocument(
        uri: String,
        content: String,
        options: FormattingOptions,
    ): List<TextEdit> {
        logger.info("[{}] formatDocument not yet implemented", displayName)
        return emptyList()
    }

    /**
     * Format a range within a document.
     *
     * LSP: textDocument/rangeFormatting
     *
     * @param uri     the document URI
     * @param content the source code content
     * @param range   the range to format
     * @param options formatting options
     * @return list of text edits to apply
     */
    fun formatRange(
        uri: String,
        content: String,
        range: Range,
        options: FormattingOptions,
    ): List<TextEdit> {
        logger.info("[{}] formatRange not yet implemented", displayName)
        return emptyList()
    }

    /**
     * Find symbols across the workspace.
     *
     * LSP: workspace/symbol
     *
     * @param query search query string
     * @return list of matching symbols
     */
    fun findWorkspaceSymbols(query: String): List<SymbolInfo> {
        logger.info("[{}] findWorkspaceSymbols not yet implemented (requires compiler)", displayName)
        return emptyList()
    }

    // ========================================================================
    // Data classes for LSP types
    // ========================================================================

    /**
     * A position in a text document (0-based line and column).
     */
    data class Position(
        val line: Int,
        val column: Int,
    )

    /**
     * A range in a text document.
     */
    data class Range(
        val start: Position,
        val end: Position,
    )

    /**
     * A text edit to apply to a document.
     */
    data class TextEdit(
        val range: Range,
        val newText: String,
    )

    /**
     * Completion item for code completion.
     */
    data class CompletionItem(
        val label: String,
        val kind: CompletionKind,
        val detail: String,
        val insertText: String,
    ) {
        enum class CompletionKind {
            CLASS,
            INTERFACE,
            METHOD,
            PROPERTY,
            VARIABLE,
            KEYWORD,
            MODULE,
        }
    }

    /**
     * Document highlight for symbol highlighting.
     */
    data class DocumentHighlight(
        val range: Range,
        val kind: HighlightKind,
    ) {
        enum class HighlightKind {
            TEXT,
            READ,
            WRITE,
        }
    }

    /**
     * Selection range with optional parent for nested selections.
     */
    data class SelectionRange(
        val range: Range,
        val parent: SelectionRange? = null,
    )

    /**
     * Folding range for code folding.
     */
    data class FoldingRange(
        val startLine: Int,
        val endLine: Int,
        val kind: FoldingKind? = null,
    ) {
        enum class FoldingKind {
            COMMENT,
            IMPORTS,
            REGION,
        }
    }

    /**
     * Document link for clickable paths.
     */
    data class DocumentLink(
        val range: Range,
        val target: String?,
        val tooltip: String? = null,
    )

    /**
     * Signature help for function calls.
     */
    data class SignatureHelp(
        val signatures: List<SignatureInfo>,
        val activeSignature: Int = 0,
        val activeParameter: Int = 0,
    )

    /**
     * Information about a function signature.
     */
    data class SignatureInfo(
        val label: String,
        val documentation: String? = null,
        val parameters: List<ParameterInfo> = emptyList(),
    )

    /**
     * Information about a function parameter.
     */
    data class ParameterInfo(
        val label: String,
        val documentation: String? = null,
    )

    /**
     * Result of prepare rename operation.
     */
    data class PrepareRenameResult(
        val range: Range,
        val placeholder: String,
    )

    /**
     * Workspace edit containing changes to multiple documents.
     */
    data class WorkspaceEdit(
        val changes: Map<String, List<TextEdit>>,
    )

    /**
     * Code action (quick fix or refactoring).
     */
    data class CodeAction(
        val title: String,
        val kind: CodeActionKind,
        val diagnostics: List<Diagnostic> = emptyList(),
        val edit: WorkspaceEdit? = null,
        val isPreferred: Boolean = false,
    ) {
        enum class CodeActionKind {
            QUICKFIX,
            REFACTOR,
            REFACTOR_EXTRACT,
            REFACTOR_INLINE,
            REFACTOR_REWRITE,
            SOURCE,
            SOURCE_ORGANIZE_IMPORTS,
        }
    }

    /**
     * Semantic tokens for enhanced syntax highlighting.
     */
    data class SemanticTokens(
        val data: List<Int>,
    )

    /**
     * Inlay hint for inline annotations.
     */
    data class InlayHint(
        val position: Position,
        val label: String,
        val kind: InlayHintKind,
        val paddingLeft: Boolean = false,
        val paddingRight: Boolean = false,
    ) {
        enum class InlayHintKind {
            TYPE,
            PARAMETER,
        }
    }

    /**
     * Formatting options from client.
     */
    data class FormattingOptions(
        val tabSize: Int,
        val insertSpaces: Boolean,
        val trimTrailingWhitespace: Boolean = false,
        val insertFinalNewline: Boolean = false,
    )
}
