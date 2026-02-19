package org.xvm.lsp.adapter

import org.xvm.lsp.adapter.XtcCompilerAdapter.CodeAction
import org.xvm.lsp.adapter.XtcCompilerAdapter.CodeAction.CodeActionKind
import org.xvm.lsp.adapter.XtcCompilerAdapter.CompletionItem
import org.xvm.lsp.adapter.XtcCompilerAdapter.CompletionItem.CompletionKind
import org.xvm.lsp.adapter.XtcCompilerAdapter.DocumentHighlight
import org.xvm.lsp.adapter.XtcCompilerAdapter.DocumentHighlight.HighlightKind
import org.xvm.lsp.adapter.XtcCompilerAdapter.FoldingRange
import org.xvm.lsp.adapter.XtcCompilerAdapter.ParameterInfo
import org.xvm.lsp.adapter.XtcCompilerAdapter.Position
import org.xvm.lsp.adapter.XtcCompilerAdapter.PrepareRenameResult
import org.xvm.lsp.adapter.XtcCompilerAdapter.Range
import org.xvm.lsp.adapter.XtcCompilerAdapter.SelectionRange
import org.xvm.lsp.adapter.XtcCompilerAdapter.SignatureHelp
import org.xvm.lsp.adapter.XtcCompilerAdapter.SignatureInfo
import org.xvm.lsp.adapter.XtcCompilerAdapter.TextEdit
import org.xvm.lsp.adapter.XtcCompilerAdapter.WorkspaceEdit
import org.xvm.lsp.adapter.XtcLanguageConstants.builtInTypeCompletions
import org.xvm.lsp.adapter.XtcLanguageConstants.keywordCompletions
import org.xvm.lsp.adapter.XtcLanguageConstants.toCompletionKind
import org.xvm.lsp.index.WorkspaceIndex
import org.xvm.lsp.index.WorkspaceIndexer
import org.xvm.lsp.model.CompilationResult
import org.xvm.lsp.model.Diagnostic
import org.xvm.lsp.model.Location
import org.xvm.lsp.model.SymbolInfo
import org.xvm.lsp.model.SymbolInfo.SymbolKind
import org.xvm.lsp.treesitter.SemanticTokenEncoder
import org.xvm.lsp.treesitter.XtcNode
import org.xvm.lsp.treesitter.XtcParser
import org.xvm.lsp.treesitter.XtcQueryEngine
import org.xvm.lsp.treesitter.XtcTree
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.extension
import kotlin.io.path.readText
import kotlin.time.measureTimedValue

/**
 * XTC Compiler Adapter implementation using Tree-sitter for fast, syntax-level intelligence.
 *
 * This adapter provides syntax-aware features without requiring the full XTC compiler:
 * - Fast incremental parsing (sub-millisecond for small changes)
 * - Error-tolerant parsing (works with incomplete/invalid code)
 * - Document symbols and outline
 * - Basic go-to-definition (same file, by name)
 * - Find references (same file, by name)
 * - Document highlight (all occurrences of symbol under cursor)
 * - Selection ranges (smart expand/shrink selection via AST)
 * - Folding ranges (collapsible declarations and blocks)
 * - Same-file rename (text-based identifier replacement)
 * - Completion (keywords, locals, visible names)
 * - Signature help (same-file method parameter info)
 * - Code actions (organize imports)
 * - Document formatting (trailing whitespace, final newline)
 * - Document links (import statement highlighting)
 * - Syntax error reporting
 *
 * Limitations (requires compiler adapter for these):
 * - Type inference and semantic types
 * - Cross-file go-to-definition and rename
 * - Semantic error reporting
 * - Smart completion based on types
 * - Inlay hints (type annotations)
 *
 * // TODO LSP: This adapter provides ~80% of LSP functionality without the compiler.
 * // For full semantic features, combine with a CompilerAdapter via CompositeAdapter.
 */
@Suppress("LoggingSimilarMessage")
class TreeSitterAdapter : AbstractXtcCompilerAdapter() {
    override val displayName: String = "TreeSitter"

    private val parser: XtcParser = XtcParser()
    private val queryEngine: XtcQueryEngine = XtcQueryEngine(parser.getLanguage())

    // ConcurrentHashMap is required because compile() is called from the LSP message thread
    // (via didOpen/didChange), while read methods like findSymbolAt(), getCompletions(), and
    // findDefinition() are called from CompletableFuture.supplyAsync on the ForkJoinPool.
    private val parsedTrees = ConcurrentHashMap<String, XtcTree>()
    private val compilationResults = ConcurrentHashMap<String, CompilationResult>()

    // Workspace index for cross-file symbol lookup (indexer has its own parser/query engine)
    private val workspaceIndex = WorkspaceIndex()
    private val indexer = WorkspaceIndexer(workspaceIndex, parser.getLanguage())
    private val indexReady = AtomicBoolean(false)

    init {
        // Perform health check to verify native library is working
        logger.info("$logPrefix ========================================")
        logger.info("$logPrefix initializing...")
        logger.info("$logPrefix Java version: {} ({})", System.getProperty("java.version"), System.getProperty("java.vendor"))
        logger.info("$logPrefix Platform: {} / {}", System.getProperty("os.name"), System.getProperty("os.arch"))
        logger.info("$logPrefix ========================================")

        if (!healthCheck()) {
            val msg =
                "$logPrefix health check FAILED - native library not working. " +
                    "Ensure native libraries are bundled and Java $MIN_JAVA_VERSION+ is used."
            logger.error(msg)
            throw IllegalStateException(msg)
        }
        logger.info("$logPrefix ready: native library loaded and verified")
    }

    /**
     * Perform health check by delegating to the parser's health check.
     * This verifies the native tree-sitter library is loaded and functional.
     */
    override fun healthCheck(): Boolean = parser.healthCheck()

    companion object {
        /**
         * Minimum Java version required for tree-sitter FFM API.
         * Update when jtreesitter dependency changes its requirements.
         * Note: Must match JreProvisioner.TARGET_VERSION in intellij-plugin.
         */
        const val MIN_JAVA_VERSION = 25
    }

    // ========================================================================
    // Workspace lifecycle
    // ========================================================================

    override fun initializeWorkspace(
        workspaceFolders: List<String>,
        progressReporter: ((String, Int) -> Unit)?,
    ) {
        logger.info("$logPrefix initializeWorkspace: {} folders: {}", workspaceFolders.size, workspaceFolders)
        indexer
            .scanWorkspace(workspaceFolders, progressReporter)
            .thenRun {
                indexReady.set(true)
                logger.info(
                    "$logPrefix workspace index ready: {} symbols in {} files",
                    workspaceIndex.symbolCount,
                    workspaceIndex.fileCount,
                )
            }.exceptionally { e ->
                logger.error("$logPrefix workspace indexing failed: {}", e.message, e)
                null
            }
    }

    override fun didChangeWatchedFile(
        uri: String,
        changeType: Int,
    ) {
        if (!indexReady.get()) return

        try {
            when (changeType) {
                1, 2 -> {
                    // Created or Changed: re-index the file
                    val path = Path.of(URI(uri))
                    if (path.extension == "x" && Files.exists(path)) {
                        val content = path.readText()
                        indexer.reindexFile(uri, content)
                        logger.info("$logPrefix re-indexed watched file: {}", uri.substringAfterLast('/'))
                    }
                }
                3 -> {
                    // Deleted: remove from index
                    indexer.removeFile(uri)
                    logger.info("$logPrefix removed deleted file from index: {}", uri.substringAfterLast('/'))
                }
            }
        } catch (e: Exception) {
            logger.warn("$logPrefix didChangeWatchedFile failed for {}: {}", uri, e.message)
        }
    }

    override fun findWorkspaceSymbols(query: String): List<SymbolInfo> {
        if (!indexReady.get()) {
            logger.info("$logPrefix findWorkspaceSymbols: index not ready yet; query='{}'", query)
            return emptyList()
        }
        val results = workspaceIndex.search(query)
        logger.info("$logPrefix findWorkspaceSymbols '{}' -> {} results", query, results.size)
        return results.map { it.toSymbolInfo() }
    }

    // ========================================================================
    // Core LSP features
    // ========================================================================

    override fun compile(
        uri: String,
        content: String,
    ): CompilationResult {
        logger.info("$logPrefix parsing {} ({} bytes)", uri, content.length)

        // Parse the content (with incremental parsing if we have an old tree)
        val oldTree = parsedTrees[uri]
        val isIncremental = oldTree != null

        val (tree, parseElapsed) =
            try {
                measureTimedValue { parser.parse(content, oldTree) }
            } catch (e: Exception) {
                logger.error("$logPrefix parse failed for {}: {}", uri, e.message)
                return CompilationResult.failure(
                    uri,
                    listOf(
                        Diagnostic.error(Location.of(uri, 0, 0), "Parse failed: ${e.message}"),
                    ),
                )
            }

        // Store the new tree first, then close the old one. This ordering is critical:
        // async handlers (codeAction, foldingRange, etc.) read from parsedTrees concurrently.
        // If we close the old tree first, in-flight handlers holding references to nodes from
        // the old tree will crash with IllegalStateException ("Already closed") when accessing
        // native FFM memory. By storing the new tree first, new requests get the fresh tree.
        // The old tree is NOT closed eagerly - its native memory is backed by Arena.global()
        // which persists for the JVM lifetime. The Tree object itself will be GC'd, and the
        // underlying C tree is freed by its finalizer. This avoids the race where in-flight
        // requests hold XtcNode references that point to already-freed native memory.
        parsedTrees[uri] = tree

        // Extract diagnostics from syntax errors
        val diagnostics = if (tree.hasErrors) collectSyntaxErrors(tree.root, uri) else emptyList()

        // Extract symbols for document outline
        val (symbols, queryElapsed) = measureTimedValue { queryEngine.findAllDeclarations(tree, uri) }

        val result = CompilationResult.withDiagnostics(uri, diagnostics, symbols)
        compilationResults[uri] = result

        // Update workspace index with fresh symbols from this file
        if (indexReady.get()) {
            indexer.reindexFile(uri, content)
        }

        logger.info(
            "$logPrefix parsed in {} ({}), {} errors, {} symbols (query: {})",
            parseElapsed,
            if (isIncremental) "incremental" else "full",
            diagnostics.size,
            symbols.size,
            queryElapsed,
        )

        return result
    }

    override fun getCachedResult(uri: String): CompilationResult? = compilationResults[uri]

    override fun findSymbolAt(
        uri: String,
        line: Int,
        column: Int,
    ): SymbolInfo? {
        logger.info("$logPrefix findSymbolAt: uri={}, line={}, column={}", uri, line, column)
        val tree = parsedTrees[uri]
        if (tree == null) {
            logger.info("$logPrefix findSymbolAt: no parsed tree for uri")
            return null
        }
        val result = queryEngine.findDeclarationAt(tree, line, column, uri)
        logger.info("$logPrefix findSymbolAt -> {}", result?.let { "'${it.name}' (${it.kind})" } ?: "null")
        return result
    }

    /**
     * TODO: Context-unaware. Cannot provide member completion after '.' or
     * type-aware suggestions. Needs compiler TypeResolver (Phase 5).
     *
     * Currently returns keywords, built-in types, document symbols, and imported names.
     * A compiler adapter would add after-dot member completion and type-filtered suggestions.
     */
    override fun getCompletions(
        uri: String,
        line: Int,
        column: Int,
    ): List<CompletionItem> {
        logger.info("$logPrefix getCompletions: uri={}, line={}, column={}", uri, line, column)
        return buildList {
            // Add keywords and built-in types
            addAll(keywordCompletions())
            addAll(builtInTypeCompletions())

            // Add symbols from current document
            parsedTrees[uri]?.let { tree ->
                queryEngine.findAllDeclarations(tree, uri).forEach { symbol ->
                    add(
                        CompletionItem(
                            label = symbol.name,
                            kind = toCompletionKind(symbol.kind),
                            detail = symbol.typeSignature ?: symbol.kind.name.lowercase(),
                            insertText = symbol.name,
                        ),
                    )
                }

                // Add imports from current document
                queryEngine.findImports(tree).forEach { importPath ->
                    val simpleName = importPath.substringAfterLast(".")
                    add(
                        CompletionItem(
                            label = simpleName,
                            kind = CompletionKind.CLASS,
                            detail = "import: $importPath",
                            insertText = simpleName,
                        ),
                    )
                }
            }
        }.also { logger.info("$logPrefix getCompletions -> {} items", it.size) }
    }

    /**
     * Find definition: same-file first, then cross-file via workspace index.
     *
     * Searches AST declarations in the current file first. If not found and the
     * workspace index is ready, falls back to cross-file lookup, preferring type
     * declarations (classes, interfaces, etc.) over methods/properties.
     */
    override fun findDefinition(
        uri: String,
        line: Int,
        column: Int,
    ): Location? {
        val (tree, name) = getIdentifierAt(uri, line, column, "definition") ?: return null

        // Same-file lookup first
        val symbols = queryEngine.findAllDeclarations(tree, uri)
        val decl = symbols.find { it.name == name }

        if (decl != null) {
            logger.info("$logPrefix definition '{}' -> same-file {}:{}", name, decl.location.startLine, decl.location.startColumn)
            return decl.location
        }

        // Cross-file fallback via workspace index
        if (indexReady.get()) {
            val indexed = workspaceIndex.findByName(name)
            if (indexed.isNotEmpty()) {
                // Prefer type declarations over methods/properties
                val typeKinds =
                    setOf(
                        SymbolKind.CLASS,
                        SymbolKind.INTERFACE,
                        SymbolKind.MIXIN,
                        SymbolKind.SERVICE,
                        SymbolKind.CONST,
                        SymbolKind.ENUM,
                        SymbolKind.MODULE,
                        SymbolKind.PACKAGE,
                    )
                val best = indexed.firstOrNull { it.kind in typeKinds } ?: indexed.first()
                logger.info(
                    "$logPrefix definition '{}' -> cross-file {} ({})",
                    name,
                    best.uri.substringAfterLast('/'),
                    best.kind,
                )
                return best.location
            }
        }

        logger.info(
            "$logPrefix definition '{}' not found ({} symbols: {})",
            name,
            symbols.size,
            symbols.take(5).joinToString { it.name },
        )
        return null
    }

    /**
     * TODO: Same-file text matching only. Cannot distinguish shadowed locals.
     * Cross-file references require compiler's semantic model (Phase 4+).
     *
     * Finds all identifier nodes with the same text in the current file's AST.
     * A compiler adapter would provide scope-aware, cross-file reference search.
     */
    override fun findReferences(
        uri: String,
        line: Int,
        column: Int,
        includeDeclaration: Boolean,
    ): List<Location> {
        val (tree, name) = getIdentifierAt(uri, line, column, "references") ?: return emptyList()
        val allRefs = queryEngine.findAllIdentifiers(tree, name, uri)
        val result =
            if (includeDeclaration) {
                allRefs
            } else {
                // Exclude the declaration location by finding where the symbol is declared
                val declLocation =
                    queryEngine
                        .findAllDeclarations(tree, uri)
                        .find { it.name == name }
                        ?.location
                if (declLocation != null) {
                    allRefs.filter { it != declLocation }
                } else {
                    allRefs
                }
            }
        logger.info("$logPrefix references '{}' -> {} found (includeDecl={})", name, result.size, includeDeclaration)
        return result
    }

    /** Resolves identifier at position, logging failures. Returns (tree, identifierText) or null. */
    private fun getIdentifierAt(
        uri: String,
        line: Int,
        column: Int,
        op: String,
    ): Pair<XtcTree, String>? {
        val tree = parsedTrees[uri] ?: return null
        val node =
            tree.nodeAt(line, column) ?: return null.also {
                logger.info("$logPrefix {}: no node at {}:{}:{}", op, uri.substringAfterLast('/'), line, column)
            }
        val id =
            findIdentifierNode(node) ?: return null.also {
                logger.info("$logPrefix {}: not an identifier at {}:{}:{} ({})", op, uri.substringAfterLast('/'), line, column, node.type)
            }
        return tree to id.text
    }

    // ========================================================================
    // Tree-sitter capable features
    // ========================================================================

    override fun getDocumentHighlights(
        uri: String,
        line: Int,
        column: Int,
    ): List<DocumentHighlight> {
        val (tree, name) = getIdentifierAt(uri, line, column, "highlight") ?: return emptyList()
        val locations = queryEngine.findAllIdentifiers(tree, name, uri)
        logger.info("$logPrefix highlight '{}' -> {} occurrences", name, locations.size)
        return locations.map { loc ->
            DocumentHighlight(
                range =
                    Range(
                        start = Position(loc.startLine, loc.startColumn),
                        end = Position(loc.endLine, loc.endColumn),
                    ),
                kind = HighlightKind.TEXT,
            )
        }
    }

    override fun getSelectionRanges(
        uri: String,
        positions: List<Position>,
    ): List<SelectionRange> {
        logger.info("$logPrefix getSelectionRanges: uri={}, positions={}", uri, positions.map { "${it.line}:${it.column}" })
        val tree = parsedTrees[uri]
        if (tree == null) {
            logger.info("$logPrefix getSelectionRanges: no parsed tree for uri")
            return emptyList()
        }
        val result = positions.map { pos -> buildSelectionRange(tree, pos.line, pos.column) }
        logger.info("$logPrefix getSelectionRanges -> {} ranges", result.size)
        return result
    }

    private fun buildSelectionRange(
        tree: XtcTree,
        line: Int,
        column: Int,
    ): SelectionRange {
        val fallback = SelectionRange(range = Range(Position(line, column), Position(line, column)))
        val node = tree.nodeAt(line, column) ?: return fallback

        // Walk up from the leaf node to the root, skipping nodes with identical ranges
        val nodes =
            buildList {
                add(node)
                generateSequence(node.parent) { it.parent }.forEach { ancestor ->
                    val prev = last()
                    if (ancestor.startLine != prev.startLine ||
                        ancestor.startColumn != prev.startColumn ||
                        ancestor.endLine != prev.endLine ||
                        ancestor.endColumn != prev.endColumn
                    ) {
                        add(ancestor)
                    }
                }
            }

        // Build the SelectionRange chain from outermost (parent) to innermost (leaf)
        return nodes.foldRight(null) { n, parent ->
            SelectionRange(
                range =
                    Range(
                        start = Position(n.startLine, n.startColumn),
                        end = Position(n.endLine, n.endColumn),
                    ),
                parent = parent,
            )
        } ?: fallback
    }

    override fun getFoldingRanges(uri: String): List<FoldingRange> {
        val tree = parsedTrees[uri] ?: return emptyList()
        return buildList {
            collectFoldingRanges(tree.root, this)
        }.also {
            logger.info("$logPrefix folding ranges -> {} found", it.size)
        }
    }

    private fun collectFoldingRanges(
        node: XtcNode,
        result: MutableList<FoldingRange>,
    ) {
        // Foldable node types: declarations and block constructs that span multiple lines
        val foldKind =
            when (node.type) {
                "class_declaration", "interface_declaration", "mixin_declaration",
                "service_declaration", "const_declaration", "enum_declaration",
                "method_declaration", "constructor_declaration",
                "module_declaration", "package_declaration",
                -> null // no special kind = code region
                "comment", "block_comment" -> FoldingRange.FoldingKind.COMMENT
                "import_list" -> FoldingRange.FoldingKind.IMPORTS
                else -> {
                    // Not a foldable node type, but recurse into children
                    node.children.forEach { collectFoldingRanges(it, result) }
                    return
                }
            }

        if (node.endLine > node.startLine) {
            result.add(FoldingRange(node.startLine, node.endLine, foldKind))
        }

        // Recurse into children for nested declarations
        node.children.forEach { collectFoldingRanges(it, result) }
    }

    /**
     * TODO: Same-file rename only. Finds the identifier AST node at the cursor position.
     * A compiler adapter would validate that the rename is semantically safe and preview
     * cross-file impacts.
     */
    override fun prepareRename(
        uri: String,
        line: Int,
        column: Int,
    ): PrepareRenameResult? {
        val tree = parsedTrees[uri] ?: return null
        val node = tree.nodeAt(line, column) ?: return null
        val id = findIdentifierNode(node) ?: return null

        logger.info("$logPrefix prepareRename '{}' at {}:{}", id.text, line, column)
        return PrepareRenameResult(
            range =
                Range(
                    start = Position(id.startLine, id.startColumn),
                    end = Position(id.endLine, id.endColumn),
                ),
            placeholder = id.text,
        )
    }

    /**
     * TODO: Same-file rename via text matching. Finds all identifier nodes with the same name
     * and produces edit operations. Cannot handle cross-file renames or validate naming conflicts.
     * A compiler adapter would rename across the workspace and update import paths.
     */
    override fun rename(
        uri: String,
        line: Int,
        column: Int,
        newName: String,
    ): WorkspaceEdit? {
        val (tree, name) = getIdentifierAt(uri, line, column, "rename") ?: return null
        val locations = queryEngine.findAllIdentifiers(tree, name, uri)

        if (locations.isEmpty()) return null

        logger.info("$logPrefix rename '{}' -> '{}' ({} occurrences)", name, newName, locations.size)
        val edits =
            locations.map { loc ->
                TextEdit(
                    range =
                        Range(
                            start = Position(loc.startLine, loc.startColumn),
                            end = Position(loc.endLine, loc.endColumn),
                        ),
                    newText = newName,
                )
            }
        return WorkspaceEdit(changes = mapOf(uri to edits))
    }

    override fun getSignatureHelp(
        uri: String,
        line: Int,
        column: Int,
    ): SignatureHelp? {
        val tree = parsedTrees[uri] ?: return null
        val node = tree.nodeAt(line, column) ?: return null

        // Walk up to find enclosing call_expression or generic_type (which the grammar
        // produces for `name(args)` calls since it can't distinguish calls from type
        // expressions without semantic analysis).
        val callTypes = setOf("call_expression", "generic_type")
        val callNode =
            generateSequence(node) { it.parent }
                .firstOrNull { it.type in callTypes && (it.childByFieldName("arguments") ?: it.childByType("arguments")) != null }
                ?: return null

        // Extract function name using the 'function' field for call_expression,
        // falling back to type_name for generic_type nodes.
        val funcName =
            callNode.childByFieldName("function")?.let { funcNode ->
                when (funcNode.type) {
                    "identifier" -> funcNode.text
                    "member_expression" -> funcNode.childByFieldName("member")?.text
                    else -> null
                }
            }
                ?: callNode
                    .childByType("type_name")
                    ?.childByType("identifier")
                    ?.text
                ?: return null

        // Count commas before the cursor to determine active parameter
        val argsNode = callNode.childByFieldName("arguments") ?: callNode.childByType("arguments")
        val activeParam =
            argsNode?.children?.count { child ->
                child.type == "," && (child.endLine < line || (child.endLine == line && child.endColumn <= column))
            } ?: 0

        // Find method declarations with matching name in same file
        val methods = queryEngine.findMethodDeclarations(tree, uri).filter { it.name == funcName }
        if (methods.isEmpty()) {
            logger.info("$logPrefix signatureHelp: no method '{}' found", funcName)
            return null
        }

        val signatures =
            methods.map { method ->
                // Find the method_declaration node to extract parameters
                val methodNode =
                    tree
                        .nodeAt(method.location.startLine, method.location.startColumn)
                        ?.let { findDeclarationNode(it, "method_declaration") }
                val params =
                    methodNode
                        ?.childByFieldName("parameters")
                        ?.let { extractParameters(it) }
                        ?: emptyList()
                val paramLabel = params.joinToString(", ") { p -> p.label }
                SignatureInfo(
                    label = "$funcName($paramLabel)",
                    parameters = params,
                )
            }

        logger.info("$logPrefix signatureHelp '{}' -> {} signatures, active param {}", funcName, signatures.size, activeParam)
        return SignatureHelp(
            signatures = signatures,
            activeParameter = activeParam,
        )
    }

    private fun findDeclarationNode(
        node: XtcNode,
        @Suppress("SameParameterValue") type: String, // TODO: Currently always "method_declaration"
    ): XtcNode? = generateSequence(node) { it.parent }.firstOrNull { it.type == type }

    private fun extractParameters(paramsNode: XtcNode): List<ParameterInfo> =
        paramsNode.children
            .filter { it.type == "parameter" }
            .map { param ->
                val typeName = param.childByFieldName("type")?.text ?: ""
                val paramName = param.childByFieldName("name")?.text ?: ""
                val label = if (typeName.isNotEmpty()) "$typeName $paramName" else paramName
                ParameterInfo(label = label)
            }

    override fun getCodeActions(
        uri: String,
        range: Range,
        diagnostics: List<Diagnostic>,
    ): List<CodeAction> =
        listOfNotNull(buildOrganizeImportsAction(uri)).also {
            logger.info("$logPrefix codeActions -> {} actions", it.size)
        }

    private fun buildOrganizeImportsAction(uri: String): CodeAction? {
        val tree = parsedTrees[uri] ?: return null
        val importNodes = tree.root.children.filter { it.type == "import_statement" }
        if (importNodes.size < 2) return null

        val sortedTexts = importNodes.map { it.text }.sorted()
        val currentTexts = importNodes.map { it.text }
        if (sortedTexts == currentTexts) return null

        // Build a single edit that replaces the import block
        val firstImport = importNodes.first()
        val lastImport = importNodes.last()
        val edit =
            TextEdit(
                range =
                    Range(
                        start = Position(firstImport.startLine, firstImport.startColumn),
                        end = Position(lastImport.endLine, lastImport.endColumn),
                    ),
                newText = sortedTexts.joinToString("\n"),
            )
        return CodeAction(
            title = "Organize Imports",
            kind = CodeActionKind.SOURCE_ORGANIZE_IMPORTS,
            edit = WorkspaceEdit(changes = mapOf(uri to listOf(edit))),
        )
    }

    override fun getDocumentLinks(
        uri: String,
        content: String,
    ): List<XtcCompilerAdapter.DocumentLink> {
        val tree = parsedTrees[uri] ?: return emptyList()
        val imports = queryEngine.findImportLocations(tree, uri)
        logger.info("$logPrefix documentLinks -> {} imports", imports.size)
        return imports.map { (importPath, loc) ->
            XtcCompilerAdapter.DocumentLink(
                range =
                    Range(
                        start = Position(loc.startLine, loc.startColumn),
                        end = Position(loc.endLine, loc.endColumn),
                    ),
                target = null, // Cannot resolve cross-file paths without compiler
                tooltip = "import $importPath",
            )
        }
    }

    override fun getSemanticTokens(uri: String): XtcCompilerAdapter.SemanticTokens? {
        val tree = parsedTrees[uri] ?: return null
        val encoder = SemanticTokenEncoder()
        val data = encoder.encode(tree.root)
        return if (data.isEmpty()) null else XtcCompilerAdapter.SemanticTokens(data)
    }

    /**
     * Release resources for a closed document.
     *
     * Closes the native tree-sitter [XtcTree] for this URI, freeing its native memory
     * (backed by `Arena.global()` / FFM). Without this, parsed trees accumulate for the
     * JVM lifetime since [compile] intentionally does NOT close old trees eagerly — see the
     * race condition comment in [compile] for details. This method is the primary mechanism
     * for reclaiming native tree memory when the editor closes a document.
     */
    override fun closeDocument(uri: String) {
        logger.info("$logPrefix closeDocument: uri={}", uri)
        parsedTrees.remove(uri)?.close()
        compilationResults.remove(uri)
    }

    override fun close() {
        indexer.close()
        workspaceIndex.clear()
        parsedTrees.values.forEach { it.close() }
        parsedTrees.clear()
        compilationResults.clear()
        queryEngine.close()
        parser.close()
    }

    private fun collectSyntaxErrors(
        node: XtcNode,
        uri: String,
    ): List<Diagnostic> =
        buildList {
            val message =
                when {
                    node.isError -> "Syntax error: unexpected '${node.text.take(20)}${if (node.text.length > 20) "..." else ""}'"
                    node.isMissing -> "Syntax error: missing ${node.type}"
                    else -> null
                }

            if (message != null) {
                add(
                    Diagnostic.error(
                        Location(uri, node.startLine, node.startColumn, node.endLine, node.endColumn),
                        message,
                    ),
                )
            }

            // Recursively check children
            node.children
                .filter { it.hasError }
                .forEach { addAll(collectSyntaxErrors(it, uri)) }
        }

    private fun findIdentifierNode(node: XtcNode): XtcNode? =
        if (node.type == "identifier" || node.type == "type_name") {
            node
        } else {
            node.parent?.takeIf { it.type == "identifier" || it.type == "type_name" }
        }
}
