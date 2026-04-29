package org.xvm.lsp.adapter.treesitter

import org.xvm.lsp.adapter.AbstractAdapter
import org.xvm.lsp.adapter.Adapter
import org.xvm.lsp.adapter.AdapterCodeActions
import org.xvm.lsp.adapter.AdapterFormatter
import org.xvm.lsp.adapter.CodeAction
import org.xvm.lsp.adapter.CodeActionQueryData
import org.xvm.lsp.adapter.CodeLens
import org.xvm.lsp.adapter.CodeLensCommand
import org.xvm.lsp.adapter.CompletionItem
import org.xvm.lsp.adapter.CompletionItem.CompletionKind
import org.xvm.lsp.adapter.DocumentHighlight
import org.xvm.lsp.adapter.DocumentHighlight.HighlightKind
import org.xvm.lsp.adapter.DocumentLink
import org.xvm.lsp.adapter.FoldingRange
import org.xvm.lsp.adapter.FormattingConfig
import org.xvm.lsp.adapter.FormattingOptions
import org.xvm.lsp.adapter.LanguageConstants.builtInTypeCompletions
import org.xvm.lsp.adapter.LanguageConstants.declarationContextBuiltInTypeCompletions
import org.xvm.lsp.adapter.LanguageConstants.declarationKeywordCompletions
import org.xvm.lsp.adapter.LanguageConstants.keywordCompletions
import org.xvm.lsp.adapter.LanguageConstants.toCompletionKind
import org.xvm.lsp.adapter.LinkedEditingRanges
import org.xvm.lsp.adapter.ParameterInfo
import org.xvm.lsp.adapter.Position
import org.xvm.lsp.adapter.PrepareRenameResult
import org.xvm.lsp.adapter.Range
import org.xvm.lsp.adapter.SelectionRange
import org.xvm.lsp.adapter.SemanticTokens
import org.xvm.lsp.adapter.SignatureHelp
import org.xvm.lsp.adapter.SignatureInfo
import org.xvm.lsp.adapter.TextEdit
import org.xvm.lsp.adapter.WorkspaceEdit
import org.xvm.lsp.index.WorkspaceIndex
import org.xvm.lsp.index.WorkspaceIndexer
import org.xvm.lsp.model.CompilationResult
import org.xvm.lsp.model.Diagnostic
import org.xvm.lsp.model.Location
import org.xvm.lsp.model.SymbolInfo
import org.xvm.lsp.model.SymbolInfo.SymbolKind
import org.xvm.lsp.model.isFileCreatedOrChanged
import org.xvm.lsp.model.isFileDeleted
import org.xvm.lsp.treesitter.SemanticTokenEncoder
import org.xvm.lsp.treesitter.SemanticTokenLegend
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
class TreeSitterAdapter : AbstractAdapter() {
    override val displayName: String = "TreeSitter"

    @Volatile
    override var editorFormattingConfig: FormattingConfig? = null

    private val parser: XtcParser = XtcParser()
    private val queryEngine: XtcQueryEngine = XtcQueryEngine(parser.getLanguage())
    private val formatter = AdapterFormatter()
    private val codeActions = AdapterCodeActions()

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
        logger.info("========================================")
        logger.info("initializing...")
        logger.info("Java version: {} ({})", System.getProperty("java.version"), System.getProperty("java.vendor"))
        logger.info("Platform: {} / {}", System.getProperty("os.name"), System.getProperty("os.arch"))
        logger.info("========================================")

        if (!healthCheck()) {
            val msg =
                "health check FAILED - native library not working. " +
                    "Ensure native libraries are bundled and Java $MIN_JAVA_VERSION+ is used."
            logger.error(msg)
            throw IllegalStateException(msg)
        }
        logger.info("ready: native library loaded and verified")
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
         * IntelliJ 2026.1+ ships with JBR 25 which satisfies this minimum.
         */
        const val MIN_JAVA_VERSION = 25

        /**
         * Conservative `http(s)` URL matcher for document-link extraction.
         * Stops at the first whitespace, quote, angle bracket, or closing paren/bracket --
         * common sentence-terminating punctuation is then trimmed in [urlTrailingTrim].
         */
        private val urlPattern = Regex("""\bhttps?://[^\s<>"'`)\]]+""")

        /** Trailing punctuation stripped from a URL match (sentence-final commas, periods, etc.). */
        private val urlTrailingTrim = charArrayOf('.', ',', ';', ':', '!', '?', ')', ']', '}')

        /** Symbol kinds that represent types (for type-position completion filtering). */
        private val typeSymbolKinds =
            setOf(
                SymbolKind.CLASS,
                SymbolKind.INTERFACE,
                SymbolKind.MIXIN,
                SymbolKind.SERVICE,
                SymbolKind.CONST,
                SymbolKind.ENUM,
                SymbolKind.MODULE,
            )

        /** Common XTC annotations for annotation completion. */
        private val commonAnnotations =
            listOf(
                "Override",
                "Abstract",
                "Lazy",
                "Inject",
                "Volatile",
                "Synchronized",
                "Deprecated",
                "RO",
            )
    }

    // ========================================================================
    // Workspace lifecycle
    // ========================================================================

    override fun initializeWorkspace(
        workspaceFolders: List<String>,
        progressReporter: ((String, Int) -> Unit)?,
    ) {
        logger.info("initializeWorkspace: {} folders: {}", workspaceFolders.size, workspaceFolders)
        indexer
            .scanWorkspace(workspaceFolders, progressReporter)
            .thenRun {
                indexReady.set(true)
                logger.info(
                    "workspace index ready: {} symbols in {} files",
                    workspaceIndex.symbolCount,
                    workspaceIndex.fileCount,
                )
            }.exceptionally { e ->
                logger.error("workspace indexing failed: {}", e.message, e)
                null
            }
    }

    override fun didChangeWatchedFile(
        uri: String,
        changeType: Int,
    ) {
        if (!indexReady.get()) {
            logger.info("didChangeWatchedFile: index not ready, ignoring {}", uri.substringAfterLast('/'))
            return
        }

        runCatching {
            when {
                isFileCreatedOrChanged(changeType) -> {
                    val path = Path.of(URI(uri))
                    if (path.extension == "x" && Files.exists(path)) {
                        indexer.reindexFile(uri, path.readText())
                        logger.info("re-indexed watched file: {}", uri.substringAfterLast('/'))
                    }
                }

                isFileDeleted(changeType) -> {
                    indexer.removeFile(uri)
                    logger.info("removed deleted file from index: {}", uri.substringAfterLast('/'))
                }
            }
        }.onFailure { e ->
            logger.warn("didChangeWatchedFile failed for {}: {}", uri, e.message)
        }
    }

    override fun findWorkspaceSymbols(query: String): List<SymbolInfo> {
        if (!indexReady.get()) {
            logger.info("findWorkspaceSymbols: index not ready yet; query='{}'", query)
            return emptyList()
        }
        val results = workspaceIndex.search(query)
        logger.info("findWorkspaceSymbols '{}' -> {} results", query, results.size)
        return results.map { it.toSymbolInfo() }
    }

    // ========================================================================
    // Core LSP features
    // ========================================================================

    override fun compile(
        uri: String,
        content: String,
    ): CompilationResult {
        logger.info("parsing {} ({} bytes)", uri, content.length)

        // Always full reparse -- oldTree retained for API compatibility but ignored by parser
        // (see XtcParser.parse() doc: incremental parsing requires Tree.edit() which we don't have)
        val oldTree = parsedTrees[uri]

        val (tree, parseElapsed) =
            try {
                measureTimedValue { parser.parse(content, oldTree) }
            } catch (e: Exception) {
                logger.error("parse failed for {}: {}", uri, e.message)
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
        } else {
            logger.info("workspace index not ready, skipping reindex for {}", uri.substringAfterLast('/'))
        }

        logger.info(
            "parsed in {}, {} errors, {} symbols (query: {})",
            parseElapsed,
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
        logger.info("findSymbolAt: uri={}, line={}, column={}", uri, line, column)
        val tree =
            parsedTrees[uri] ?: run {
                logger.info("findSymbolAt: no parsed tree for uri")
                return null
            }
        return queryEngine.findDeclarationAt(tree, line, column, uri).also { result ->
            logger.info("findSymbolAt -> {}", result?.let { "'${it.name}' (${it.kind})" } ?: "null")
        }
    }

    /**
     * Context-aware completion: filters and augments suggestions based on the AST
     * context at the cursor position and the trigger character.
     *
     * | Context | What to show |
     * |---------|-------------|
     * | After `.` | Same-class members (methods, properties) from enclosing class body |
     * | In type position | Only types (classes, interfaces, enums, etc.) |
     * | After `@` | Known annotation names |
     * | In `import` statement | Qualified names from workspace index |
     * | Default | Keywords + types + locals + imports (current behavior) |
     */
    override fun getCompletions(
        uri: String,
        line: Int,
        column: Int,
        triggerCharacter: String?,
    ): List<CompletionItem> {
        logger.info("getCompletions: uri={}, line={}, column={}, trigger={}", uri, line, column, triggerCharacter)

        val tree = parsedTrees[uri]
        val contextNode =
            tree?.nodeAt(line, maxOf(0, column - 1))
        val context =
            if (tree != null) {
                classifyCompletionContext(contextNode, triggerCharacter)
            } else {
                CompletionContext.DEFAULT
            }
        logger.info(
            "getCompletions: context={} node={} text='{}' indexReady={}",
            context,
            contextNode?.type,
            contextNode?.text?.replace("\n", "\\n")?.take(60),
            indexReady.get(),
        )

        return buildList {
            when (context) {
                CompletionContext.MEMBER -> {
                    // After '.': show members from the enclosing class body
                    if (tree != null) {
                        val members = collectClassMembers(tree, uri, line, column)
                        logger.info("getCompletions: member-context candidates={}", members.map { it.label })
                        addAll(members)
                    }
                }

                CompletionContext.TYPE -> {
                    // In type position: only type completions (classes, interfaces, etc.)
                    val declarationKeywords = declarationKeywordCompletions()
                    val declarationContext =
                        contextNode?.text?.firstOrNull()?.isLowerCase() == true &&
                            generateSequence(contextNode) { it.parent }
                                .any { it.type in setOf("module_body", "class_body", "package_body", "enum_body", "block") }
                    val builtIns =
                        if (declarationContext) {
                            declarationContextBuiltInTypeCompletions()
                        } else {
                            builtInTypeCompletions()
                        }
                    val declarationKeywordLabels = declarationKeywords.map(CompletionItem::label)
                    val builtInLabels = builtIns.map(CompletionItem::label)
                    logger.info(
                        "getCompletions: type-context declarationContext={} declarationKeywords={} builtIns={}",
                        declarationContext,
                        declarationKeywordLabels,
                        builtInLabels,
                    )
                    addAll(declarationKeywords)
                    addAll(builtIns)
                    if (tree != null) {
                        val fileTypes =
                            queryEngine
                                .findAllDeclarations(tree, uri)
                                .filter { it.kind in typeSymbolKinds }
                        logger.info("getCompletions: type-context fileTypes={}", fileTypes.map { it.name })
                        fileTypes.forEach { symbol ->
                            add(
                                CompletionItem(
                                    label = symbol.name,
                                    kind = toCompletionKind(symbol.kind),
                                    detail = symbol.typeSignature ?: symbol.kind.name.lowercase(),
                                    insertText = symbol.name,
                                ),
                            )
                        }
                        // Add workspace index types
                        if (indexReady.get()) {
                            val workspaceTypes = workspaceIndexTypeCompletions()
                            logger.info("getCompletions: type-context workspaceTypes(sample)={}", workspaceTypes.take(10).map { it.label })
                            addAll(workspaceTypes)
                        }
                    }
                }

                CompletionContext.ANNOTATION -> {
                    // After '@': known annotation names
                    val common =
                        commonAnnotations.map { name ->
                            CompletionItem(
                                label = name,
                                kind = CompletionKind.CLASS,
                                detail = "annotation",
                                insertText = name,
                            )
                        }
                    logger.info("getCompletions: annotation-context common={}", common.map { it.label })
                    addAll(common)
                    // Also add annotations found in the current file
                    if (tree != null) {
                        val fileAnnotations = collectAnnotationNames(tree.root)
                        logger.info("getCompletions: annotation-context fileAnnotations={}", fileAnnotations)
                        fileAnnotations.forEach { name ->
                            add(
                                CompletionItem(
                                    label = name,
                                    kind = CompletionKind.CLASS,
                                    detail = "annotation",
                                    insertText = name,
                                ),
                            )
                        }
                    }
                }

                CompletionContext.IMPORT -> {
                    // In import statement: qualified names from workspace index
                    if (indexReady.get()) {
                        val importSymbols = workspaceIndex.search("", limit = 200)
                        logger.info(
                            "getCompletions: import-context workspaceSymbols={} sample={}",
                            importSymbols.size,
                            importSymbols.take(10).map { it.qualifiedName },
                        )
                        importSymbols.forEach { symbol ->
                            add(
                                CompletionItem(
                                    label = symbol.qualifiedName,
                                    kind = toCompletionKind(symbol.kind),
                                    detail = symbol.kind.name.lowercase(),
                                    insertText = symbol.qualifiedName,
                                ),
                            )
                        }
                    }
                }

                CompletionContext.BODY -> {
                    if (tree != null) {
                        val declarations = queryEngine.findAllDeclarations(tree, uri)
                        logger.info("getCompletions: body-context declarations={}", declarations.map { "${it.kind}:${it.name}" })
                        declarations.forEach { symbol ->
                            add(
                                CompletionItem(
                                    label = symbol.name,
                                    kind = toCompletionKind(symbol.kind),
                                    detail = symbol.typeSignature ?: symbol.kind.name.lowercase(),
                                    insertText = symbol.name,
                                ),
                            )
                        }
                    }
                    val flowKeywords =
                        setOf(
                            "if",
                            "else",
                            "switch",
                            "case",
                            "default",
                            "for",
                            "while",
                            "do",
                            "break",
                            "continue",
                            "return",
                            "try",
                            "catch",
                            "finally",
                            "throw",
                            "using",
                            "assert",
                            "new",
                            "this",
                            "super",
                            "outer",
                            "val",
                            "var",
                            "True",
                            "False",
                            "Null",
                        )
                    val bodyKeywords = keywordCompletions().filter { it.label in flowKeywords }
                    val builtIns = builtInTypeCompletions()
                    logger.info(
                        "getCompletions: body-context keywords={} builtIns(sample)={}",
                        bodyKeywords.map { it.label },
                        builtIns.take(10).map { it.label },
                    )
                    addAll(bodyKeywords)
                    addAll(builtIns)
                    if (indexReady.get()) {
                        val workspaceTypes = workspaceIndexTypeCompletions()
                        logger.info("getCompletions: body-context workspaceTypes(sample)={}", workspaceTypes.take(10).map { it.label })
                        addAll(workspaceTypes)
                    }
                    // File-level declarations and imports are visible names inside any body, so
                    // module-level @Inject properties, top-level methods, and imported types must
                    // be offered alongside keywords and built-ins. Without this, a cursor inside
                    // a function body cannot complete `console` even though `@Inject Console console;`
                    // is declared at module scope.
                    if (tree != null) {
                        val declarations = queryEngine.findAllDeclarations(tree, uri)
                        declarations.forEach { symbol ->
                            add(
                                CompletionItem(
                                    label = symbol.name,
                                    kind = toCompletionKind(symbol.kind),
                                    detail = symbol.typeSignature ?: symbol.kind.name.lowercase(),
                                    insertText = symbol.name,
                                ),
                            )
                        }
                        val imports = queryEngine.findImports(tree)
                        imports.forEach { importPath ->
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
                }

                CompletionContext.DEFAULT -> {
                    // Full default: keywords + built-in types + document symbols + imports
                    val keywords = keywordCompletions()
                    val builtIns = builtInTypeCompletions()
                    logger.info(
                        "getCompletions: default-context keywords={} builtIns={}",
                        keywords.map { it.label },
                        builtIns.map { it.label },
                    )
                    addAll(keywords)
                    addAll(builtIns)
                    if (tree != null) {
                        val declarations = queryEngine.findAllDeclarations(tree, uri)
                        logger.info("getCompletions: default-context declarations={}", declarations.map { "${it.kind}:${it.name}" })
                        declarations.forEach { symbol ->
                            add(
                                CompletionItem(
                                    label = symbol.name,
                                    kind = toCompletionKind(symbol.kind),
                                    detail = symbol.typeSignature ?: symbol.kind.name.lowercase(),
                                    insertText = symbol.name,
                                ),
                            )
                        }
                        val imports = queryEngine.findImports(tree)
                        logger.info("getCompletions: default-context imports={}", imports)
                        imports.forEach { importPath ->
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
                }
            }
        }.distinctBy { it.label }.also { logger.info("getCompletions -> {} items ({})", it.size, context) }
    }

    // ========================================================================
    // Completion context classification
    // ========================================================================

    private enum class CompletionContext {
        MEMBER, // After '.' — show class members
        TYPE, // In type position — only types
        ANNOTATION, // After '@' — annotation names
        IMPORT, // Inside import statement — qualified names
        BODY, // Inside module/class/method bodies — visible names first
        DEFAULT, // Everything
    }

    private fun classifyCompletionContext(
        node: XtcNode?,
        triggerCharacter: String?,
    ): CompletionContext {
        if (node == null) return CompletionContext.DEFAULT

        // Check trigger character first
        if (triggerCharacter == ".") return CompletionContext.MEMBER

        // Walk ancestry to determine context
        val ancestry = generateSequence(node) { it.parent }.toList()
        for (ancestor in ancestry) {
            when (ancestor.type) {
                "member_expression" -> {
                    // If we're after the '.' in a member expression
                    val dot = ancestor.children.firstOrNull { it.type == "." }
                    if (dot != null && node.startColumn >= dot.endColumn) {
                        return CompletionContext.MEMBER
                    }
                }

                "import_statement" -> {
                    return CompletionContext.IMPORT
                }

                "module_body", "class_body", "package_body", "enum_body", "block" -> {
                    return CompletionContext.BODY
                }

                "type_expression", "type_name", "generic_type" -> {
                    return CompletionContext.TYPE
                }

                "annotation" -> {
                    return CompletionContext.ANNOTATION
                }
            }
        }

        // Check if the node text starts with '@'
        if (node.text.startsWith("@")) return CompletionContext.ANNOTATION

        // Check extends/implements context -> TYPE
        val prevSiblingTypes = setOf("extends", "implements", "incorporates", "delegates")
        val parent = node.parent
        if (parent != null) {
            val nodeIndex = parent.children.indexOf(node)
            if (nodeIndex > 0) {
                val prevSibling = parent.children[nodeIndex - 1]
                if (prevSibling.text in prevSiblingTypes) return CompletionContext.TYPE
            }
        }

        return CompletionContext.DEFAULT
    }

    /**
     * Collect method and property names from the enclosing class body for member completion.
     */
    private fun collectClassMembers(
        tree: XtcTree,
        uri: String,
        line: Int,
        column: Int,
    ): List<CompletionItem> {
        val node = tree.nodeAt(line, maxOf(0, column - 1)) ?: return emptyList()

        // Walk up to find the enclosing class body
        val classBodyTypes = setOf("class_body", "module_body", "package_body", "enum_body")
        val classBody =
            generateSequence(node) { it.parent }
                .firstOrNull { it.type in classBodyTypes }
                ?: return emptyList()

        // Extract declarations from the class body
        return buildList {
            for (child in classBody.children) {
                val name = child.childByFieldName("name")?.text ?: continue
                val kind =
                    when (child.type) {
                        "method_declaration", "function_declaration" -> CompletionKind.METHOD
                        "constructor_declaration" -> CompletionKind.METHOD
                        "property_declaration" -> CompletionKind.PROPERTY
                        else -> continue
                    }
                add(
                    CompletionItem(
                        label = name,
                        kind = kind,
                        detail = child.type.replace("_declaration", "").replace("_", " "),
                        insertText = name,
                    ),
                )
            }
        }
    }

    private fun workspaceIndexTypeCompletions(): List<CompletionItem> =
        workspaceIndex
            .search("", limit = 200)
            .filter { it.kind in typeSymbolKinds }
            .map { symbol ->
                CompletionItem(
                    label = symbol.name,
                    kind = toCompletionKind(symbol.kind),
                    detail = "import: ${symbol.qualifiedName}",
                    insertText = symbol.name,
                )
            }

    private fun collectAnnotationNames(node: XtcNode): Set<String> {
        val names = mutableSetOf<String>()
        if (node.type == "annotation") {
            val name =
                node.childByFieldName("name")?.text
                    ?: node.children.firstOrNull { it.type == "identifier" }?.text
            if (name != null) names.add(name)
        }
        node.children.forEach { names.addAll(collectAnnotationNames(it)) }
        return names
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

        // Scope-aware resolution: walk the AST upward from the cursor and prefer
        // the nearest enclosing local variable, parameter, or class/module member
        // before consulting the workspace index. Without this, a local variable
        // shadowing a workspace symbol of the same name resolves cross-file --
        // e.g. cmd-click on a local `whitespace` would jump to Lexer.x's
        // `Boolean whitespace;` instead of the local declaration.
        val scoped = queryEngine.resolveByNameInScope(tree, line, column, name, uri)
        if (scoped != null) {
            logger.info("definition '{}' -> scope-local {}:{}", name, scoped.startLine, scoped.startColumn)
            return scoped
        }

        // Same-file top-level lookup (covers anything findAllDeclarations indexes
        // that resolveByNameInScope didn't reach -- mostly redundant with the
        // class/module body scope walk, kept as a defensive fallback).
        val symbols = queryEngine.findAllDeclarations(tree, uri)
        val decl = symbols.find { it.name == name }

        if (decl != null) {
            logger.info("definition '{}' -> same-file {}:{}", name, decl.location.startLine, decl.location.startColumn)
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
                    "definition '{}' -> cross-file {} ({})",
                    name,
                    best.uri.substringAfterLast('/'),
                    best.kind,
                )
                logger.info(
                    "definition '{}' strategy=workspace-index candidates={}",
                    name,
                    indexed.map { "${it.kind}:${it.qualifiedName}" },
                )
                return best.location
            }
        }

        logger.info(
            "definition '{}' not found ({} symbols: {})",
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
                val declLocation =
                    queryEngine
                        .findAllDeclarations(tree, uri)
                        .find { it.name == name }
                        ?.location
                declLocation?.let { decl -> allRefs.filter { it != decl } } ?: allRefs
            }
        logger.info("references '{}' -> {} found (includeDecl={})", name, result.size, includeDeclaration)
        if (result.isNotEmpty()) {
            result.forEach { loc ->
                logger.info("  {}:{}:{}", loc.uri.substringAfterLast('/'), loc.startLine + 1, loc.startColumn + 1)
            }
        }
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
                logger.info("{}: no node at {}:{}:{}", op, uri.substringAfterLast('/'), line, column)
            }
        val id =
            findIdentifierNode(node) ?: return null.also {
                logger.info("{}: not an identifier at {}:{}:{} ({})", op, uri.substringAfterLast('/'), line, column, node.type)
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
        logger.info("highlight '{}' -> {} occurrences", name, locations.size)
        return locations.map { loc ->
            val node = tree.nodeAt(loc.startLine, loc.startColumn)
            val kind = if (node != null && isAssignmentTarget(node)) HighlightKind.WRITE else HighlightKind.READ
            val range = Range(Position(loc.startLine, loc.startColumn), Position(loc.endLine, loc.endColumn))
            DocumentHighlight(range, kind)
        }
    }

    override fun getLinkedEditingRanges(
        uri: String,
        line: Int,
        column: Int,
    ): LinkedEditingRanges? {
        val (tree, name) = getIdentifierAt(uri, line, column, "linkedEditingRange") ?: return null
        val locations = queryEngine.findAllIdentifiers(tree, name, uri)
        if (locations.size < 2) return null
        logger.info("linkedEditingRange '{}' -> {} ranges", name, locations.size)
        return LinkedEditingRanges(
            ranges =
                locations.map { loc ->
                    Range(Position(loc.startLine, loc.startColumn), Position(loc.endLine, loc.endColumn))
                },
        )
    }

    private fun isAssignmentTarget(node: XtcNode): Boolean {
        val parent = node.parent ?: return false
        return when (parent.type) {
            "assignment_statement", "assignment_expression" -> {
                parent.childByFieldName("left")?.let { isOrContains(it, node) } == true
            }

            "variable_declaration", "parameter" -> {
                parent.childByFieldName("name")?.let { isSameNode(it, node) } == true
            }

            else -> {
                false
            }
        }
    }

    private fun isOrContains(
        container: XtcNode,
        target: XtcNode,
    ): Boolean = isSameNode(container, target) || container.children.any { isOrContains(it, target) }

    private fun isSameNode(
        a: XtcNode,
        b: XtcNode,
    ): Boolean =
        a.startLine == b.startLine && a.startColumn == b.startColumn &&
            a.endLine == b.endLine && a.endColumn == b.endColumn

    override fun getSelectionRanges(
        uri: String,
        positions: List<Position>,
    ): List<SelectionRange> {
        logger.info("getSelectionRanges: uri={}, positions={}", uri, positions.map { "${it.line}:${it.column}" })
        val tree =
            parsedTrees[uri] ?: run {
                logger.info("getSelectionRanges: no parsed tree for uri")
                return emptyList()
            }
        return positions.map { pos -> buildSelectionRange(tree, pos.line, pos.column) }.also {
            logger.info("getSelectionRanges -> {} ranges", it.size)
        }
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
                range = Range(Position(n.startLine, n.startColumn), Position(n.endLine, n.endColumn)),
                parent = parent,
            )
        } ?: fallback
    }

    override fun getFoldingRanges(uri: String): List<FoldingRange> {
        logger.info("getFoldingRanges: uri={}", uri.substringAfterLast('/'))
        val tree =
            parsedTrees[uri] ?: run {
                logger.info("getFoldingRanges: no parsed tree for uri")
                return emptyList()
            }
        return buildList {
            collectFoldingRanges(tree.root, this)
            mergeConsecutiveLineComments(tree.root, this)
        }.also {
            logger.info("getFoldingRanges -> {} ranges", it.size)
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
                -> {
                    null
                }

                // no special kind = code region

                "comment", "line_comment", "block_comment", "doc_comment" -> {
                    FoldingRange.FoldingKind.COMMENT
                }

                "import_list" -> {
                    FoldingRange.FoldingKind.IMPORTS
                }

                else -> {
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

    private fun mergeConsecutiveLineComments(
        root: XtcNode,
        result: MutableList<FoldingRange>,
    ) {
        val lineComments = mutableListOf<XtcNode>()
        collectLineComments(root, lineComments)
        lineComments.sortBy { it.startLine }

        var groupStart = -1
        var groupEnd = -1
        for (comment in lineComments) {
            if (groupStart < 0) {
                groupStart = comment.startLine
                groupEnd = comment.endLine
            } else if (comment.startLine <= groupEnd + 1) {
                groupEnd = comment.endLine
            } else {
                if (groupEnd > groupStart) {
                    result.add(FoldingRange(groupStart, groupEnd, FoldingRange.FoldingKind.COMMENT))
                }
                groupStart = comment.startLine
                groupEnd = comment.endLine
            }
        }
        if (groupEnd > groupStart) {
            result.add(FoldingRange(groupStart, groupEnd, FoldingRange.FoldingKind.COMMENT))
        }
    }

    private fun collectLineComments(
        node: XtcNode,
        result: MutableList<XtcNode>,
    ) {
        if ((node.type == "comment" || node.type == "line_comment") && node.startLine == node.endLine) {
            result.add(node)
        }
        node.children.forEach { collectLineComments(it, result) }
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

        logger.info("prepareRename '{}' at {}:{}", id.text, line, column)
        return PrepareRenameResult(
            range = Range(Position(id.startLine, id.startColumn), Position(id.endLine, id.endColumn)),
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

        logger.info("rename '{}' -> '{}' ({} occurrences)", name, newName, locations.size)
        val edits =
            locations.map { loc ->
                val range = Range(Position(loc.startLine, loc.startColumn), Position(loc.endLine, loc.endColumn))
                TextEdit(range, newName)
            }
        return WorkspaceEdit(mapOf(uri to edits))
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
            logger.info("signatureHelp: no method '{}' found", funcName)
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

        logger.info("signatureHelp '{}' -> {} signatures, active param {}", funcName, signatures.size, activeParam)
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
    ): List<CodeAction> {
        logger.info(
            "getCodeActions: uri={}, range={}:{}-{}:{}, {} diagnostics",
            uri.substringAfterLast('/'),
            range.start.line,
            range.start.column,
            range.end.line,
            range.end.column,
            diagnostics.size,
        )
        val tree = parsedTrees[uri] ?: return emptyList()
        val queryData =
            CodeActionQueryData(
                imports = queryEngine.findImports(tree),
                importLocations = queryEngine.findImportLocations(tree, uri),
                declarations = queryEngine.findAllDeclarations(tree, uri),
                findIdentifiers = { name -> queryEngine.findAllIdentifiers(tree, name, uri) },
            )
        return codeActions.getCodeActions(tree, uri, range, queryData, workspaceIndex, indexReady.get())
    }

    // ========================================================================
    // Code lenses (run/compile actions on module declarations)
    // ========================================================================

    override fun getCodeLenses(uri: String): List<CodeLens> {
        logger.info("getCodeLenses: uri={}", uri.substringAfterLast('/'))
        val tree = parsedTrees[uri] ?: return emptyList()
        val declarations = queryEngine.findAllDeclarations(tree, uri)

        return buildList {
            for (decl in declarations) {
                if (decl.kind != SymbolKind.MODULE) continue

                val range =
                    Range(
                        start = Position(decl.location.startLine, decl.location.startColumn),
                        end = Position(decl.location.endLine, decl.location.endColumn),
                    )

                // "Run" lens — modules are the entry point in XTC
                add(
                    CodeLens(
                        range = range,
                        command =
                            CodeLensCommand(
                                title = "\u25B6 Run ${decl.name}",
                                command = "xtc.runModule",
                                arguments = listOf(uri, decl.name),
                            ),
                    ),
                )
            }
        }.also {
            logger.info("getCodeLenses -> {} lenses", it.size)
        }
    }

    // ========================================================================
    // Document formatting (delegates to AdapterFormatter)
    // ========================================================================

    override fun formatDocument(
        uri: String,
        content: String,
        options: FormattingOptions,
    ): List<TextEdit> {
        val tree =
            parsedTrees[uri] ?: run {
                logger.info("formatDocument: no parsed tree for {}, falling back to base", uri.substringAfterLast('/'))
                return super.formatDocument(uri, content, options)
            }
        val config = FormattingConfig.resolve(uri, options, editorFormattingConfig)
        return formatter.formatDocument(tree, content, config, options)
    }

    override fun formatRange(
        uri: String,
        content: String,
        range: Range,
        options: FormattingOptions,
    ): List<TextEdit> {
        val tree =
            parsedTrees[uri] ?: run {
                logger.info("formatRange: no parsed tree for {}, falling back to base", uri.substringAfterLast('/'))
                return super.formatRange(uri, content, range, options)
            }
        val config = FormattingConfig.resolve(uri, options, editorFormattingConfig)
        return formatter.formatRange(tree, content, range, config, options)
    }

    // ========================================================================
    // On-type formatting (delegates to AdapterFormatter)
    // ========================================================================

    override fun onTypeFormatting(
        uri: String,
        line: Int,
        column: Int,
        ch: String,
        options: FormattingOptions,
    ): List<TextEdit> {
        val tree =
            parsedTrees[uri] ?: run {
                logger.info("onTypeFormatting: no parsed tree for {}", uri.substringAfterLast('/'))
                return emptyList()
            }
        val config = FormattingConfig.resolve(uri, options, editorFormattingConfig)
        logger.info(
            "onTypeFormatting: uri={} line={} column={} ch='{}' config={}",
            uri.substringAfterLast('/'),
            line,
            column,
            ch,
            config,
        )
        return formatter.onTypeFormatting(tree, line, column, ch, config)
    }

    /**
     * Emit document links for `http(s)` URLs that appear inside comments and string literals.
     *
     * Imports deliberately do NOT participate -- navigation between an `import` and its
     * declaration is the job of `textDocument/definition` (Cmd/Ctrl-click), which is
     * position-aware and knows the difference between a package component and a type
     * component. Document links are reserved for free-text content where there is no
     * AST-level "declaration" to jump to (URLs in `// see https://...`, etc.).
     */
    override fun getDocumentLinks(
        uri: String,
        content: String,
    ): List<DocumentLink> {
        val tree = parsedTrees[uri] ?: return emptyList()
        val hosts = queryEngine.findCommentAndStringNodes(tree, uri)
        val links =
            buildList {
                for ((text, loc) in hosts) {
                    urlPattern.findAll(text).forEach { match ->
                        // Strip trailing punctuation that's almost never part of the URL itself
                        // (sentences in comments end with these, e.g. "see https://example.com.")
                        val trimmed = match.value.trimEnd(*urlTrailingTrim)
                        if (trimmed.isEmpty()) return@forEach
                        val range = rangeWithinText(text, match.range.first, trimmed.length, loc)
                        add(DocumentLink(range, trimmed, trimmed))
                    }
                }
            }
        logger.info("getDocumentLinks: {} URL links across {} comment/string nodes", links.size, hosts.size)
        return links
    }

    /**
     * Convert an offset+length within a host node's text into an absolute LSP [Range].
     * URLs cannot contain whitespace, so the match is always single-line, but the host
     * node (e.g., a block comment) may span many lines, so we walk the prefix counting
     * newlines to find the absolute (line, column) for the start.
     */
    private fun rangeWithinText(
        text: String,
        matchStart: Int,
        matchLen: Int,
        host: Location,
    ): Range {
        var line = host.startLine
        var col = host.startColumn
        for (i in 0 until matchStart) {
            if (text[i] == '\n') {
                line++
                col = 0
            } else {
                col++
            }
        }
        val startLine = line
        val startCol = col
        // URL has no newlines -- end is on the same line, +matchLen columns
        return Range(Position(startLine, startCol), Position(startLine, startCol + matchLen))
    }

    override fun getSemanticTokens(uri: String): SemanticTokens? {
        logger.info("getSemanticTokens: uri={}", uri.substringAfterLast('/'))
        val tree =
            parsedTrees[uri] ?: run {
                logger.info("getSemanticTokens: no parsed tree for uri")
                return null
            }
        val data = SemanticTokenEncoder().encode(tree.root)
        val tokenTypeCounts =
            data
                .chunked(5)
                .mapNotNull { chunk ->
                    val tokenTypeIndex = chunk.getOrNull(3) ?: return@mapNotNull null
                    SemanticTokenLegend.tokenTypes.getOrNull(tokenTypeIndex)
                }.groupingBy { it }
                .eachCount()
        logger.info("getSemanticTokens -> {} data items ({} tokens) types={}", data.size, data.size / 5, tokenTypeCounts)
        return data.takeIf { it.isNotEmpty() }?.let { SemanticTokens(it) }
    }

    /**
     * Release resources for a closed document.
     *
     * Closes the native tree-sitter [XtcTree] for this URI, freeing its native memory
     * (backed by `Arena.global()` / FFM). Without this, parsed trees accumulate for the
     * JVM lifetime since [compile] intentionally does NOT close old trees eagerly -- see the
     * race condition comment in [compile] for details. This method is the primary mechanism
     * for reclaiming native tree memory when the editor closes a document.
     */
    override fun closeDocument(uri: String) {
        logger.info("closeDocument: uri={}", uri)
        parsedTrees.remove(uri)?.close()
        compilationResults.remove(uri)
    }

    override fun close() {
        logger.info("close: shutting down adapter")
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
