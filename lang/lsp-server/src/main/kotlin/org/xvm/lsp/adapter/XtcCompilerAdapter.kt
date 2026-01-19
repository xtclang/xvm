package org.xvm.lsp.adapter

import org.xvm.lsp.model.CompilationResult
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
 * ## Extended Interface
 *
 * For full LSP support, see [XtcCompilerAdapterFull] which adds:
 * - rename(), prepareRename()
 * - getCodeActions()
 * - formatDocument(), formatRange()
 * - getSemanticTokens()
 * - getSignatureHelp()
 * - getFoldingRanges()
 * - getInlayHints()
 * - Call/Type hierarchy
 * - Workspace symbols
 * - Debug adapter (DAP) support
 *
 * @see TreeSitterAdapter for syntax-level intelligence
 * @see MockXtcCompilerAdapter for testing
 * @see XtcCompilerAdapterFull for extended interface
 */
interface XtcCompilerAdapter {

    /**
     * Human-readable name of this adapter for display in logs and UI.
     * Examples: "Mock (regex)", "Tree-sitter", "XTC Compiler"
     */
    val displayName: String
        get() = this::class.simpleName ?: "Unknown"

    /**
     * Compile a source file and return the result.
     *
     * @param uri     the document URI
     * @param content the source code content
     * @return compilation result with diagnostics and symbols
     */
    fun compile(uri: String, content: String): CompilationResult

    /**
     * Find the symbol at a specific position.
     *
     * @param uri    the document URI
     * @param line   0-based line number
     * @param column 0-based column number
     * @return the symbol at that position, if any
     */
    fun findSymbolAt(uri: String, line: Int, column: Int): SymbolInfo?

    /**
     * Get hover information for a position.
     *
     * @param uri    the document URI
     * @param line   0-based line number
     * @param column 0-based column number
     * @return hover text (Markdown), if available
     */
    fun getHoverInfo(uri: String, line: Int, column: Int): String?

    /**
     * Get completion suggestions at a position.
     *
     * @param uri    the document URI
     * @param line   0-based line number
     * @param column 0-based column number
     * @return list of completion items
     */
    fun getCompletions(uri: String, line: Int, column: Int): List<CompletionItem>

    /**
     * Find the definition of the symbol at a position.
     *
     * @param uri    the document URI
     * @param line   0-based line number
     * @param column 0-based column number
     * @return location of the definition, if found
     */
    fun findDefinition(uri: String, line: Int, column: Int): Location?

    /**
     * Find all references to the symbol at a position.
     *
     * @param uri              the document URI
     * @param line             0-based line number
     * @param column           0-based column number
     * @param includeDeclaration whether to include the declaration itself
     * @return list of reference locations
     */
    fun findReferences(uri: String, line: Int, column: Int, includeDeclaration: Boolean): List<Location>

    /**
     * Completion item for code completion.
     */
    data class CompletionItem(
        val label: String,
        val kind: CompletionKind,
        val detail: String,
        val insertText: String
    ) {
        enum class CompletionKind {
            CLASS, INTERFACE, METHOD, PROPERTY, VARIABLE, KEYWORD, MODULE
        }
    }
}
