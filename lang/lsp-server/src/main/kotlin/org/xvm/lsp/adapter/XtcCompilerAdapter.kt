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
     * **LSP capability:** Triggered by `textDocument/didOpen` and `textDocument/didChange`.
     * The client sends the full document text; the server parses it and publishes diagnostics.
     *
     * **Editor activation:** Automatic — triggered when a `.x` file is opened or edited.
     *
     * **Adapter implementations:**
     * - *Mock:* Regex-scans for module/class/interface/method/property patterns and ERROR markers.
     * - *TreeSitter:* Incremental native parse with error-tolerant grammar; extracts symbols via queries.
     * - *Compiler:* Full semantic compilation with type resolution and cross-file analysis.
     *
     * **Compiler upgrade path:** A compiler adapter would produce semantic diagnostics (type errors,
     * unresolved references) and a richer symbol table with resolved types and cross-file links.
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
     * **LSP capability:** Used internally by hover, definition, and references — not directly
     * exposed as an LSP method, but underpins several user-visible features.
     *
     * **Adapter implementations:**
     * - *Mock:* Checks if position falls within any compiled symbol's line range.
     * - *TreeSitter:* Walks the parse tree to find the AST node at the position, then queries
     *   declarations to match it.
     * - *Compiler:* Resolves the fully-qualified symbol with type information.
     *
     * **Compiler upgrade path:** Would return symbols with resolved types, cross-file qualified
     * names, and documentation extracted from doc comments.
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
     * **LSP capability:** `textDocument/hover` — shown when the user hovers the mouse over a
     * symbol. Displays a tooltip with type signature, documentation, and other info.
     *
     * **Editor activation:**
     * - *IntelliJ:* Hover mouse over a symbol, or Ctrl+Q (Quick Documentation)
     * - *VS Code:* Hover mouse over a symbol
     *
     * **Adapter implementations:**
     * - *Mock/TreeSitter:* Default in [AbstractXtcCompilerAdapter] — calls [findSymbolAt] and
     *   formats the symbol's kind, name, and type signature as Markdown.
     * - *Compiler:* Would add resolved types, inferred generics, and extracted doc comments.
     *
     * **Compiler upgrade path:** Full type signatures (e.g., `Person implements Hashable, Const`)
     * and rendered XDoc documentation.
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
     * **LSP capability:** `textDocument/completion` — provides code completion suggestions as the
     * user types. Triggered by `.`, `:`, `<`, or explicit request.
     *
     * **Editor activation:**
     * - *IntelliJ:* Ctrl+Space (Basic), or type and wait for auto-popup
     * - *VS Code:* Ctrl+Space, or type and wait for auto-popup
     *
     * **Adapter implementations:**
     * - *Mock:* Returns XTC keywords, built-in types, and symbols from the current document.
     * - *TreeSitter:* Same as Mock, plus import-derived names and symbols from AST queries.
     *   Context-unaware (cannot provide member completion after `.`).
     * - *Compiler:* Type-aware completion with member access, method overloads, and import suggestions.
     *
     * **Compiler upgrade path:** Context-sensitive completions: after `.` show members, after `:`
     * show types, inside `import` show available modules/packages.
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
     * **LSP capability:** `textDocument/definition` — navigates to where a symbol is declared.
     *
     * **Editor activation:**
     * - *IntelliJ:* Ctrl+Click on a symbol, Ctrl+B, or F12
     * - *VS Code:* Ctrl+Click on a symbol, or F12
     *
     * **Adapter implementations:**
     * - *Mock:* Returns the symbol's own location (same-file, name-based match only).
     * - *TreeSitter:* Searches AST declarations for a matching name in the same file.
     * - *Compiler:* Cross-file resolution via import paths and fully-qualified names.
     *
     * **Compiler upgrade path:** Cross-file go-to-definition, resolving imports, inherited
     * members, and overloaded method signatures.
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
     * **LSP capability:** `textDocument/references` — shows all usages of a symbol.
     *
     * **Editor activation:**
     * - *IntelliJ:* Alt+F7 (Find Usages), or Shift+F12
     * - *VS Code:* Shift+F12, or right-click → Find All References
     *
     * **Adapter implementations:**
     * - *Mock:* Returns only the declaration itself (when `includeDeclaration` is true), no
     *   actual usage search.
     * - *TreeSitter:* Text-matches the identifier name across all AST nodes in the same file.
     *   Cannot distinguish shadowed locals or cross-file references.
     * - *Compiler:* Semantic reference search across the entire workspace.
     *
     * **Compiler upgrade path:** Cross-file references, distinguishing reads vs writes, and
     * filtering by scope (e.g., only references within the same module).
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
     * **LSP capability:** `textDocument/documentHighlight` — highlights all occurrences of the
     * symbol under the cursor in the same document. Shown as background color emphasis.
     *
     * **Editor activation:** Automatic — click on any identifier to highlight all occurrences.
     *
     * **Adapter implementations:**
     * - *Mock:* Whole-word text search across all lines of the cached document content.
     * - *TreeSitter:* Finds the identifier AST node at the position, then queries all identifier
     *   nodes with the same text. Returns all as `TEXT` kind.
     * - *Compiler:* Semantic highlights distinguishing `READ` vs `WRITE` access.
     *
     * **Compiler upgrade path:** Distinguish read/write highlights, skip string literals and
     * comments, handle shadowed variables correctly.
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
     * **LSP capability:** `textDocument/selectionRange` — powers smart expand/shrink selection.
     * Returns a chain of nested ranges from the innermost token to the outermost declaration.
     *
     * **Editor activation:**
     * - *IntelliJ:* Ctrl+W (Expand Selection) / Ctrl+Shift+W (Shrink)
     * - *VS Code:* Shift+Alt+Right (Expand) / Shift+Alt+Left (Shrink)
     *
     * **Adapter implementations:**
     * - *Mock:* Returns empty (requires AST structure for meaningful results).
     * - *TreeSitter:* Walks up from the leaf node at the position to the root, building a chain
     *   of progressively larger ranges (identifier → expression → statement → block → class).
     * - *Compiler:* Same as TreeSitter (AST-based; no semantic info needed).
     *
     * **Compiler upgrade path:** Minimal — tree-sitter already provides excellent selection ranges.
     * A compiler adapter would use the same approach from its own AST.
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
     * **LSP capability:** `textDocument/foldingRange` — provides collapsible regions in the
     * editor gutter (classes, methods, imports, comments).
     *
     * **Editor activation:**
     * - *IntelliJ:* Click fold arrows in gutter; Ctrl+Shift+Minus (fold all) / Ctrl+Shift+Plus (unfold all)
     * - *VS Code:* Click fold arrows in gutter; Ctrl+Shift+[ (fold) / Ctrl+Shift+] (unfold)
     *
     * **Adapter implementations:**
     * - *Mock:* Brace-matching (`{`/`}` pairs) plus import-line grouping.
     * - *TreeSitter:* AST node boundaries for declarations, blocks, comments, and import lists.
     *   More accurate than brace matching (handles string literals, comments correctly).
     * - *Compiler:* Same as TreeSitter (structural feature, no semantic info needed).
     *
     * **Compiler upgrade path:** Minimal — tree-sitter provides excellent folding ranges.
     * A compiler adapter could add region markers from structured comments.
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
     * **LSP capability:** `textDocument/documentLink` — makes import paths and other references
     * clickable in the editor, allowing quick navigation.
     *
     * **Editor activation:** Automatic — import paths appear as clickable links (Ctrl+Click).
     *
     * **Adapter implementations:**
     * - *Mock:* Regex-matches `import` statements and returns the path portion as a link.
     * - *TreeSitter:* Extracts import nodes from the AST and returns their locations.
     *   Target is null (cannot resolve cross-file paths without compiler).
     * - *Compiler:* Resolves import paths to actual file URIs for clickable navigation.
     *
     * **Compiler upgrade path:** Resolve `target` URIs so clicking an import opens the
     * referenced module/package file.
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
     * **LSP capability:** `textDocument/signatureHelp` — shows parameter hints when the user
     * types `(` or `,` inside a function call. Highlights the active parameter.
     *
     * **Editor activation:**
     * - *IntelliJ:* Type `(` after a method name, or Ctrl+P inside argument list
     * - *VS Code:* Type `(` after a method name, or Ctrl+Shift+Space inside argument list
     *
     * **Adapter implementations:**
     * - *Mock:* Returns null (cannot extract method parameters from regex patterns).
     * - *TreeSitter:* Walks up to enclosing `call_expression`, finds the called method's
     *   declaration in the same file, extracts parameter nodes, and counts commas to determine
     *   the active parameter index.
     * - *Compiler:* Resolves overloaded methods, cross-file signatures, and default values.
     *
     * **Compiler upgrade path:** Cross-file method resolution, overload disambiguation,
     * default parameter values, and documentation for each parameter.
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
     * Prepare rename operation — check if rename is valid at position.
     *
     * **LSP capability:** `textDocument/prepareRename` — called before a rename to verify the
     * position is on a renamable identifier and to highlight the range to be changed.
     *
     * **Editor activation:** Called automatically as part of the rename flow (see [rename]).
     *
     * **Adapter implementations:**
     * - *Mock:* Finds the word at the position via regex and returns its range and text.
     * - *TreeSitter:* Finds the identifier AST node at the position and returns its exact range.
     * - *Compiler:* Validates the rename is semantically valid (not a keyword, not cross-module).
     *
     * **Compiler upgrade path:** Reject renames of built-in types, warn about cross-file impact,
     * and validate the new name doesn't conflict with existing declarations.
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
     * **LSP capability:** `textDocument/rename` — renames a symbol and returns a workspace edit
     * with all text changes. The editor applies all edits atomically.
     *
     * **Editor activation:**
     * - *IntelliJ:* Shift+F6 on an identifier, or right-click → Refactor → Rename
     * - *VS Code:* F2 on an identifier, or right-click → Rename Symbol
     *
     * **Adapter implementations:**
     * - *Mock:* Whole-word text replacement across all lines in the same file.
     * - *TreeSitter:* Finds all identifier AST nodes with the same text in the same file and
     *   produces edits for each occurrence.
     * - *Compiler:* Cross-file rename with semantic analysis, updating imports and references.
     *
     * **Compiler upgrade path:** Cross-file rename across the workspace, updating import paths,
     * and handling constructor references and type aliases.
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
     * **LSP capability:** `textDocument/codeAction` — provides the lightbulb menu with quick
     * fixes and refactoring suggestions. Actions can include workspace edits or commands.
     *
     * **Editor activation:**
     * - *IntelliJ:* Alt+Enter (Intentions), or click lightbulb icon in gutter
     * - *VS Code:* Ctrl+. (Quick Fix), or click lightbulb icon
     *
     * **Adapter implementations:**
     * - *Mock:* Offers "Organize Imports" when import statements are detected and unsorted.
     * - *TreeSitter:* Same as Mock — detects unsorted import nodes from the AST and offers
     *   a single edit to sort them.
     * - *Compiler:* Quick fixes for diagnostics (add import, fix typo), refactorings (extract
     *   method, inline variable).
     *
     * **Compiler upgrade path:** Diagnostic-driven quick fixes, extract/inline refactorings,
     * and "add missing override" suggestions.
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
     * **LSP capability:** `textDocument/semanticTokens/full` — provides token-level semantic
     * highlighting that supplements TextMate grammars. Distinguishes fields vs locals vs
     * parameters, type names vs variable names, etc.
     *
     * **Editor activation:** Automatic — applied as an overlay on top of TextMate highlighting.
     *
     * **Adapter implementations:**
     * - *Mock:* Returns null (no type information available).
     * - *TreeSitter:* Returns null (could partially classify tokens from AST, but without type
     *   resolution the benefit over TextMate is limited).
     * - *Compiler:* Full semantic token classification with type-aware highlighting.
     *
     * **Compiler upgrade path:** Classify every token with its semantic role (variable, parameter,
     * property, type, function, enum member, etc.) for rich editor highlighting.
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
     * **LSP capability:** `textDocument/inlayHint` — shows inline annotations in the editor
     * for inferred types and parameter names (e.g., `val x` shows `: Int` after the variable).
     *
     * **Editor activation:** Automatic — hints appear inline when enabled.
     * - *IntelliJ:* Settings → Editor → Inlay Hints (toggle per category)
     * - *VS Code:* `editor.inlayHints.enabled` setting
     *
     * **Adapter implementations:**
     * - *Mock:* Returns empty (requires type inference).
     * - *TreeSitter:* Returns empty (requires type inference).
     * - *Compiler:* Provides inferred type annotations and parameter name hints.
     *
     * **Compiler upgrade path:** Show inferred types for `val` declarations, parameter names
     * at call sites, and return type hints for methods without explicit return types.
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
     * **LSP capability:** `textDocument/formatting` — formats the entire document.
     *
     * **Editor activation:**
     * - *IntelliJ:* Ctrl+Alt+L (Reformat Code)
     * - *VS Code:* Shift+Alt+F (Format Document)
     *
     * **Adapter implementations:**
     * - *Mock:* Removes trailing whitespace from all lines and inserts final newline if missing.
     * - *TreeSitter:* Same as Mock (basic formatting; AST-aware indentation is possible but not
     *   yet implemented).
     * - *Compiler:* Full code formatting with XTC style rules, indentation, and alignment.
     *
     * **Compiler upgrade path:** AST-aware formatting with configurable style rules (brace
     * placement, indentation, blank lines between declarations).
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
     * **LSP capability:** `textDocument/rangeFormatting` — formats only the selected range.
     *
     * **Editor activation:**
     * - *IntelliJ:* Select text, then Ctrl+Alt+L
     * - *VS Code:* Select text, then Ctrl+K Ctrl+F (Format Selection)
     *
     * **Adapter implementations:**
     * - *Mock:* Removes trailing whitespace only on lines within the specified range.
     *   Does not insert final newline (that's a whole-document concern).
     * - *TreeSitter:* Same as Mock (range-scoped trailing whitespace removal).
     * - *Compiler:* Full formatting within the range, re-indenting and aligning.
     *
     * **Compiler upgrade path:** AST-aware range formatting that adjusts indentation relative
     * to the surrounding context.
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
     * **LSP capability:** `workspace/symbol` — provides workspace-wide symbol search.
     *
     * **Editor activation:**
     * - *IntelliJ:* Ctrl+T (Go to Symbol), or Navigate → Symbol
     * - *VS Code:* Ctrl+T (Go to Symbol in Workspace)
     *
     * **Adapter implementations:**
     * - *Mock:* Returns empty (no workspace index).
     * - *TreeSitter:* Returns empty (single-file parsing only).
     * - *Compiler:* Searches a workspace-wide symbol index.
     *
     * **Compiler upgrade path:** Build and maintain a cross-file symbol index that supports
     * fuzzy matching, filtering by kind, and ranking by relevance.
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
