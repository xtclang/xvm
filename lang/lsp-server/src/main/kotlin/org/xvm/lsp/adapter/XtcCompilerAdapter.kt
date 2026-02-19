package org.xvm.lsp.adapter

import org.xvm.lsp.model.CompilationResult
import org.xvm.lsp.model.Diagnostic
import org.xvm.lsp.model.Location
import org.xvm.lsp.model.SymbolInfo
import java.io.Closeable

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
interface XtcCompilerAdapter : Closeable {
    override fun close() {}

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
     * **Editor activation:** Automatic -- triggered when a `.x` file is opened or edited.
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
     * Get the cached compilation result for a document, if available.
     *
     * Returns the result from the most recent [compile] call for this URI,
     * or `null` if the document has not been compiled yet or has been closed.
     * Used by the language server to avoid redundant re-compilation for requests
     * like `documentSymbol` that can use the cached result.
     */
    fun getCachedResult(uri: String): CompilationResult? = null

    /**
     * Find the symbol at a specific position.
     *
     * **LSP capability:** Used internally by hover, definition, and references -- not directly
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
     * **LSP capability:** `textDocument/hover` -- shown when the user hovers the mouse over a
     * symbol. Displays a tooltip with type signature, documentation, and other info.
     *
     * **Editor activation:**
     * - *IntelliJ:* Hover mouse over a symbol, or Ctrl+Q (Quick Documentation)
     * - *VS Code:* Hover mouse over a symbol
     *
     * **Adapter implementations:**
     * - *Mock/TreeSitter:* Default in [AbstractXtcCompilerAdapter] -- calls [findSymbolAt] and
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
     * **LSP capability:** `textDocument/completion` -- provides code completion suggestions as the
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
     * **LSP capability:** `textDocument/definition` -- navigates to where a symbol is declared.
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
     * **LSP capability:** `textDocument/references` -- shows all usages of a symbol.
     *
     * **Editor activation:**
     * - *IntelliJ:* Alt+F7 (Find Usages), or Shift+F12
     * - *VS Code:* Shift+F12, or right-click -> Find All References
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
    // Workspace lifecycle
    // ========================================================================

    /**
     * Initialize the workspace after the server has started.
     *
     * Called once during `initialize` with the workspace folder paths.
     * Implementations can use this to start background indexing of all `*.x` files.
     *
     * @param workspaceFolders list of workspace folder paths (file system paths, not URIs)
     * @param progressReporter optional callback for progress: (message, percentComplete)
     */
    fun initializeWorkspace(
        workspaceFolders: List<String>,
        progressReporter: ((String, Int) -> Unit)? = null,
    ) {}

    /**
     * Notification that a watched file has changed on disk.
     *
     * Called when the client reports file creation, modification, or deletion
     * via `workspace/didChangeWatchedFiles`.
     *
     * @param uri the file URI
     * @param changeType 1 = Created, 2 = Changed, 3 = Deleted (LSP FileChangeType values)
     */
    fun didChangeWatchedFile(
        uri: String,
        changeType: Int,
    ) {}

    /**
     * Notification that a document has been closed by the editor.
     *
     * Implementations should release any resources held for the document (parsed trees,
     * cached compilation results, etc.) to prevent memory accumulation.
     *
     * @param uri the document URI
     */
    fun closeDocument(uri: String) {}

    // ========================================================================
    // Tree-sitter capable features (syntax-based, no semantic analysis)
    // ========================================================================

    /**
     * Get document highlights for a symbol at a position.
     *
     * **LSP capability:** `textDocument/documentHighlight` -- highlights all occurrences of the
     * symbol under the cursor in the same document. Shown as background color emphasis.
     *
     * **Editor activation:** Automatic -- click on any identifier to highlight all occurrences.
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
    ): List<DocumentHighlight>

    /**
     * Get selection ranges for positions (smart selection expansion).
     *
     * **LSP capability:** `textDocument/selectionRange` -- powers smart expand/shrink selection.
     * Returns a chain of nested ranges from the innermost token to the outermost declaration.
     *
     * **Editor activation:**
     * - *IntelliJ:* Ctrl+W (Expand Selection) / Ctrl+Shift+W (Shrink)
     * - *VS Code:* Shift+Alt+Right (Expand) / Shift+Alt+Left (Shrink)
     *
     * **Adapter implementations:**
     * - *Mock:* Returns empty (requires AST structure for meaningful results).
     * - *TreeSitter:* Walks up from the leaf node at the position to the root, building a chain
     *   of progressively larger ranges (identifier -> expression -> statement -> block -> class).
     * - *Compiler:* Same as TreeSitter (AST-based; no semantic info needed).
     *
     * **Compiler upgrade path:** Minimal -- tree-sitter already provides excellent selection ranges.
     * A compiler adapter would use the same approach from its own AST.
     *
     * @param uri       the document URI
     * @param positions list of positions to get selection ranges for
     * @return list of selection ranges (one per input position)
     */
    fun getSelectionRanges(
        uri: String,
        positions: List<Position>,
    ): List<SelectionRange>

    /**
     * Get folding ranges for a document.
     *
     * **LSP capability:** `textDocument/foldingRange` -- provides collapsible regions in the
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
     * **Compiler upgrade path:** Minimal -- tree-sitter provides excellent folding ranges.
     * A compiler adapter could add region markers from structured comments.
     *
     * @param uri the document URI
     * @return list of folding ranges
     */
    fun getFoldingRanges(uri: String): List<FoldingRange>

    /**
     * Get document links (clickable paths in imports, etc.).
     *
     * **LSP capability:** `textDocument/documentLink` -- makes import paths and other references
     * clickable in the editor, allowing quick navigation.
     *
     * **Editor activation:** Automatic -- import paths appear as clickable links (Ctrl+Click).
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
    ): List<DocumentLink>

    // ========================================================================
    // Semantic features (require full compiler)
    // ========================================================================

    /**
     * Get signature help for a function call at a position.
     *
     * **LSP capability:** `textDocument/signatureHelp` -- shows parameter hints when the user
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
    ): SignatureHelp?

    /**
     * Prepare rename operation -- check if rename is valid at position.
     *
     * **LSP capability:** `textDocument/prepareRename` -- called before a rename to verify the
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
    ): PrepareRenameResult?

    /**
     * Perform rename operation.
     *
     * **LSP capability:** `textDocument/rename` -- renames a symbol and returns a workspace edit
     * with all text changes. The editor applies all edits atomically.
     *
     * **Editor activation:**
     * - *IntelliJ:* Shift+F6 on an identifier, or right-click -> Refactor -> Rename
     * - *VS Code:* F2 on an identifier, or right-click -> Rename Symbol
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
    ): WorkspaceEdit?

    /**
     * Get code actions for a range (quick fixes, refactorings).
     *
     * **LSP capability:** `textDocument/codeAction` -- provides the lightbulb menu with quick
     * fixes and refactoring suggestions. Actions can include workspace edits or commands.
     *
     * **Editor activation:**
     * - *IntelliJ:* Alt+Enter (Intentions), or click lightbulb icon in gutter
     * - *VS Code:* Ctrl+. (Quick Fix), or click lightbulb icon
     *
     * **Adapter implementations:**
     * - *Mock:* Offers "Organize Imports" when import statements are detected and unsorted.
     * - *TreeSitter:* Same as Mock -- detects unsorted import nodes from the AST and offers
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
    ): List<CodeAction>

    /**
     * Get semantic tokens for enhanced syntax highlighting.
     *
     * **LSP capability:** `textDocument/semanticTokens/full` -- provides token-level semantic
     * highlighting that supplements TextMate grammars. Distinguishes fields vs locals vs
     * parameters, type names vs variable names, etc.
     *
     * **Editor activation:** Automatic -- applied as an overlay on top of TextMate highlighting.
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
    fun getSemanticTokens(uri: String): SemanticTokens?

    /**
     * Get inlay hints (inline type annotations, parameter names).
     *
     * **LSP capability:** `textDocument/inlayHint` -- shows inline annotations in the editor
     * for inferred types and parameter names (e.g., `val x` shows `: Int` after the variable).
     *
     * **Editor activation:** Automatic -- hints appear inline when enabled.
     * - *IntelliJ:* Settings -> Editor -> Inlay Hints (toggle per category)
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
    ): List<InlayHint>

    /**
     * Format an entire document.
     *
     * **LSP capability:** `textDocument/formatting` -- formats the entire document.
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
    ): List<TextEdit>

    /**
     * Format a range within a document.
     *
     * **LSP capability:** `textDocument/rangeFormatting` -- formats only the selected range.
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
    ): List<TextEdit>

    /**
     * Find symbols across the workspace.
     *
     * **LSP capability:** `workspace/symbol` -- provides workspace-wide symbol search.
     *
     * **Editor activation:**
     * - *IntelliJ:* Ctrl+T (Go to Symbol), or Navigate -> Symbol
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
    fun findWorkspaceSymbols(query: String): List<SymbolInfo>

    // ========================================================================
    // Planned features (not yet implemented)
    // ========================================================================
    //
    // These methods correspond to LSP capabilities described in plan-next-steps-lsp.md.
    // Default implementations in AbstractXtcCompilerAdapter log the call with full
    // input parameters, so the log trace shows exactly what was requested and that
    // the feature is not yet available -- making it easy to plug in later.
    //
    // ========================================================================

    /**
     * Find the declaration of the symbol at a position.
     *
     * **LSP capability:** `textDocument/declaration` -- navigates to the declaration site
     * (as opposed to the definition site). In XTC's single-file-per-type model, this is
     * less important than in C/C++ where declaration and definition can be in separate files.
     *
     * **Editor activation:**
     * - *IntelliJ:* Ctrl+B on a symbol (if distinct from definition)
     * - *VS Code:* Right-click -> Go to Declaration
     *
     * **Adapter implementations:**
     * - *Mock/TreeSitter:* Not implemented -- cannot distinguish declaration from definition.
     * - *Compiler:* Resolves declaration site from semantic analysis.
     *
     * @param uri    the document URI
     * @param line   0-based line number
     * @param column 0-based column number
     * @return location of the declaration, if found
     */
    fun findDeclaration(
        uri: String,
        line: Int,
        column: Int,
    ): Location?

    /**
     * Find the type definition of the symbol at a position.
     *
     * **LSP capability:** `textDocument/typeDefinition` -- navigates to the type of an
     * expression or variable. E.g., from a variable `name` of type `String`, jumps to the
     * `String` class definition.
     *
     * **Editor activation:**
     * - *IntelliJ:* Ctrl+Shift+B on a variable or expression
     * - *VS Code:* Right-click -> Go to Type Definition
     *
     * **Adapter implementations:**
     * - *Mock/TreeSitter:* Not implemented -- requires type inference.
     * - *Compiler:* Resolves the type of the expression and navigates to the type declaration.
     *
     * @param uri    the document URI
     * @param line   0-based line number
     * @param column 0-based column number
     * @return location of the type definition, if found
     */
    fun findTypeDefinition(
        uri: String,
        line: Int,
        column: Int,
    ): Location?

    /**
     * Find implementations of the interface or abstract method at a position.
     *
     * **LSP capability:** `textDocument/implementation` -- finds all concrete implementations
     * of an interface, abstract class, or abstract method.
     *
     * **Editor activation:**
     * - *IntelliJ:* Ctrl+Alt+B on an interface/abstract method
     * - *VS Code:* Right-click -> Go to Implementations
     *
     * **Adapter implementations:**
     * - *Mock/TreeSitter:* Not implemented -- requires type hierarchy and semantic analysis.
     * - *Compiler:* Walks the type hierarchy index to find all implementors.
     *
     * @param uri    the document URI
     * @param line   0-based line number
     * @param column 0-based column number
     * @return list of implementation locations
     */
    fun findImplementation(
        uri: String,
        line: Int,
        column: Int,
    ): List<Location>

    /**
     * Prepare type hierarchy for the symbol at a position.
     *
     * **LSP capability:** `typeHierarchy/prepareTypeHierarchy` -- resolves the type at the cursor
     * and returns it as a TypeHierarchyItem. This is the entry point; the client then calls
     * [getSupertypes] and [getSubtypes] to expand the tree.
     *
     * **Editor activation:**
     * - *IntelliJ:* Ctrl+H (Type Hierarchy)
     * - *VS Code:* Right-click -> Show Type Hierarchy
     *
     * **Adapter implementations:**
     * - *Mock:* Not implemented.
     * - *TreeSitter:* Could extract the type declaration and its extends/implements clauses
     *   from the AST (Phase 1 -- tree-sitter can parse these syntactically).
     * - *Compiler:* Full type resolution with generics and conditional mixins.
     *
     * @param uri    the document URI
     * @param line   0-based line number
     * @param column 0-based column number
     * @return list of type hierarchy items at the position
     */
    fun prepareTypeHierarchy(
        uri: String,
        line: Int,
        column: Int,
    ): List<TypeHierarchyItem>

    /**
     * Get supertypes for a type hierarchy item.
     *
     * **LSP capability:** `typeHierarchy/supertypes` -- returns the parents of a type
     * (extends, implements, incorporates).
     *
     * @param item the type hierarchy item to get supertypes for
     * @return list of supertype items
     */
    fun getSupertypes(item: TypeHierarchyItem): List<TypeHierarchyItem>

    /**
     * Get subtypes for a type hierarchy item.
     *
     * **LSP capability:** `typeHierarchy/subtypes` -- returns all types that extend/implement
     * the given type.
     *
     * @param item the type hierarchy item to get subtypes for
     * @return list of subtype items
     */
    fun getSubtypes(item: TypeHierarchyItem): List<TypeHierarchyItem>

    /**
     * Prepare call hierarchy for the symbol at a position.
     *
     * **LSP capability:** `callHierarchy/prepare` -- resolves the function/method at the cursor
     * and returns it as a CallHierarchyItem. The client then calls [getIncomingCalls] and
     * [getOutgoingCalls] to navigate the call graph.
     *
     * **Editor activation:**
     * - *IntelliJ:* Ctrl+Alt+H (Call Hierarchy)
     * - *VS Code:* Right-click -> Show Call Hierarchy
     *
     * **Adapter implementations:**
     * - *Mock:* Not implemented.
     * - *TreeSitter:* Could syntactically identify the method at cursor (Phase 2).
     * - *Compiler:* Full semantic resolution with overload disambiguation.
     *
     * @param uri    the document URI
     * @param line   0-based line number
     * @param column 0-based column number
     * @return list of call hierarchy items at the position
     */
    fun prepareCallHierarchy(
        uri: String,
        line: Int,
        column: Int,
    ): List<CallHierarchyItem>

    /**
     * Get incoming calls for a call hierarchy item (who calls this function).
     *
     * **LSP capability:** `callHierarchy/incomingCalls` -- returns all call sites that invoke
     * the given function/method.
     *
     * @param item the call hierarchy item to find callers for
     * @return list of incoming calls with caller info and call-site ranges
     */
    fun getIncomingCalls(item: CallHierarchyItem): List<CallHierarchyIncomingCall>

    /**
     * Get outgoing calls for a call hierarchy item (what does this function call).
     *
     * **LSP capability:** `callHierarchy/outgoingCalls` -- returns all functions/methods that
     * the given function calls.
     *
     * @param item the call hierarchy item to find callees for
     * @return list of outgoing calls with callee info and call-site ranges
     */
    fun getOutgoingCalls(item: CallHierarchyItem): List<CallHierarchyOutgoingCall>

    /**
     * Get code lenses for a document.
     *
     * **LSP capability:** `textDocument/codeLens` -- provides actionable inline annotations
     * above declarations: reference counts ("3 references"), "Run Test", "Debug", "Implement".
     *
     * **Editor activation:** Automatic -- annotations appear above methods, classes, etc.
     *
     * **Adapter implementations:**
     * - *Mock/TreeSitter:* Could show reference counts once workspace index exists.
     * - *Compiler:* Precise reference counts, virtual dispatch resolution, test discovery,
     *   run/debug buttons.
     *
     * @param uri the document URI
     * @return list of code lenses
     */
    fun getCodeLenses(uri: String): List<CodeLens>

    /**
     * Resolve a code lens (fill in the command/action lazily).
     *
     * **LSP capability:** `codeLens/resolve` -- called by the client when a code lens becomes
     * visible to fill in its command. Allows lazy computation for performance.
     *
     * @param lens the code lens to resolve
     * @return the resolved code lens with command filled in
     */
    fun resolveCodeLens(lens: CodeLens): CodeLens

    /**
     * Format on type -- auto-format after typing a trigger character.
     *
     * **LSP capability:** `textDocument/onTypeFormatting` -- auto-indent when pressing Enter,
     * `}`, or `;`. Tree-sitter provides enough AST context to determine correct indentation.
     *
     * **Editor activation:** Automatic -- triggered after typing the trigger character.
     *
     * **Adapter implementations:**
     * - *Mock:* Not implemented.
     * - *TreeSitter:* Could determine indentation level from AST context.
     * - *Compiler:* Full context-aware formatting.
     *
     * @param uri     the document URI
     * @param line    0-based line number where the character was typed
     * @param column  0-based column number
     * @param ch      the character that was typed (trigger character)
     * @param options formatting options
     * @return list of text edits to apply
     */
    fun onTypeFormatting(
        uri: String,
        line: Int,
        column: Int,
        ch: String,
        options: FormattingOptions,
    ): List<TextEdit>

    /**
     * Get linked editing ranges for the symbol at a position.
     *
     * **LSP capability:** `textDocument/linkedEditingRange` -- when renaming an identifier,
     * all related occurrences update simultaneously in real-time (before committing the rename).
     *
     * **Editor activation:** Automatic -- start editing an identifier and linked ranges
     * update in real-time.
     *
     * **Adapter implementations:**
     * - *Mock:* Not implemented.
     * - *TreeSitter:* Could identify the declaration and its same-file usages.
     * - *Compiler:* Semantic linked editing with scope awareness.
     *
     * @param uri    the document URI
     * @param line   0-based line number
     * @param column 0-based column number
     * @return linked editing ranges, if available
     */
    fun getLinkedEditingRanges(
        uri: String,
        line: Int,
        column: Int,
    ): LinkedEditingRanges?

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

    // ========================================================================
    // Data classes for planned features (type hierarchy, call hierarchy, etc.)
    // ========================================================================

    /**
     * An item in a type hierarchy (a type and its position in the source).
     *
     * Used by [prepareTypeHierarchy], [getSupertypes], [getSubtypes].
     */
    data class TypeHierarchyItem(
        val name: String,
        val kind: SymbolInfo.SymbolKind,
        val uri: String,
        val range: Range,
        val selectionRange: Range,
        val detail: String? = null,
    )

    /**
     * An item in a call hierarchy (a function/method and its position).
     *
     * Used by [prepareCallHierarchy], [getIncomingCalls], [getOutgoingCalls].
     */
    data class CallHierarchyItem(
        val name: String,
        val kind: SymbolInfo.SymbolKind,
        val uri: String,
        val range: Range,
        val selectionRange: Range,
        val detail: String? = null,
    )

    /**
     * An incoming call to a call hierarchy item (who calls it).
     *
     * @param from       the calling function/method
     * @param fromRanges the specific call-site ranges within the caller
     */
    data class CallHierarchyIncomingCall(
        val from: CallHierarchyItem,
        val fromRanges: List<Range>,
    )

    /**
     * An outgoing call from a call hierarchy item (what it calls).
     *
     * @param to         the called function/method
     * @param fromRanges the specific call-site ranges within the caller
     */
    data class CallHierarchyOutgoingCall(
        val to: CallHierarchyItem,
        val fromRanges: List<Range>,
    )

    /**
     * A code lens (actionable inline annotation above a declaration).
     *
     * @param range   the range this code lens applies to
     * @param command the command to execute when clicked (null until resolved)
     */
    data class CodeLens(
        val range: Range,
        val command: CodeLensCommand? = null,
    )

    /**
     * A command associated with a code lens.
     *
     * @param title     display text (e.g., "3 references", "Run Test")
     * @param command   the command identifier to execute
     * @param arguments optional arguments to the command
     */
    data class CodeLensCommand(
        val title: String,
        val command: String,
        val arguments: List<Any> = emptyList(),
    )

    /**
     * Linked editing ranges -- ranges that should be edited simultaneously.
     *
     * @param ranges      the ranges that are linked
     * @param wordPattern optional regex pattern that the new text must match
     */
    data class LinkedEditingRanges(
        val ranges: List<Range>,
        val wordPattern: String? = null,
    )
}
