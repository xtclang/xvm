package org.xvm.lsp.adapter

import org.xvm.lsp.adapter.XtcCompilerAdapter.CompletionItem
import org.xvm.lsp.adapter.XtcCompilerAdapter.CompletionItem.CompletionKind
import org.xvm.lsp.adapter.XtcLanguageConstants.builtInTypeCompletions
import org.xvm.lsp.adapter.XtcLanguageConstants.keywordCompletions
import org.xvm.lsp.adapter.XtcLanguageConstants.toCompletionKind
import org.xvm.lsp.model.CompilationResult
import org.xvm.lsp.model.Diagnostic
import org.xvm.lsp.model.Location
import org.xvm.lsp.model.SymbolInfo
import org.xvm.lsp.treesitter.XtcNode
import org.xvm.lsp.treesitter.XtcParser
import org.xvm.lsp.treesitter.XtcQueryEngine
import org.xvm.lsp.treesitter.XtcTree
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.measureTime

/**
 * XTC Compiler Adapter implementation using Tree-sitter for fast, syntax-level intelligence.
 *
 * This adapter provides syntax-aware features without requiring the full XTC compiler:
 * - Fast incremental parsing (sub-millisecond for small changes)
 * - Error-tolerant parsing (works with incomplete/invalid code)
 * - Document symbols and outline
 * - Basic go-to-definition (same file, by name)
 * - Find references (same file, by name)
 * - Completion (keywords, locals, visible names)
 * - Syntax error reporting
 *
 * Limitations (requires compiler adapter for these):
 * - Type inference and semantic types
 * - Cross-file go-to-definition
 * - Semantic error reporting
 * - Smart completion based on types
 * - Rename refactoring
 *
 * // TODO LSP: This adapter provides ~70% of LSP functionality without the compiler.
 * // For full semantic features, combine with a CompilerAdapter via CompositeAdapter.
 */
class TreeSitterAdapter : AbstractXtcCompilerAdapter() {
    override val displayName: String = "TreeSitter"

    private val parser: XtcParser = XtcParser()
    private val queryEngine: XtcQueryEngine = XtcQueryEngine(parser.getLanguage())
    private val parsedTrees = ConcurrentHashMap<String, XtcTree>()
    private val compilationResults = ConcurrentHashMap<String, CompilationResult>()

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

        /**
         * Execute a block and return its result along with elapsed duration.
         */
        private inline fun <T> timed(block: () -> T): Pair<T, Duration> {
            var result: T
            val duration = measureTime { result = block() }
            return result to duration
        }
    }

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
                timed { parser.parse(content, oldTree) }
            } catch (e: Exception) {
                logger.error("$logPrefix parse failed for {}: {}", uri, e.message)
                return CompilationResult.failure(
                    uri,
                    listOf(
                        Diagnostic.error(Location.of(uri, 0, 0), "Parse failed: ${e.message}"),
                    ),
                )
            }

        // Close old tree if it exists
        oldTree?.close()
        parsedTrees[uri] = tree

        // Extract diagnostics from syntax errors
        val diagnostics = if (tree.hasErrors) collectSyntaxErrors(tree.root, uri) else emptyList()

        // Extract symbols for document outline
        val (symbols, queryElapsed) = timed { queryEngine.findAllDeclarations(tree, uri) }

        val result = CompilationResult.withDiagnostics(uri, diagnostics, symbols)
        compilationResults[uri] = result

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

    override fun findSymbolAt(
        uri: String,
        line: Int,
        column: Int,
    ): SymbolInfo? {
        val tree = parsedTrees[uri] ?: return null
        return queryEngine.findDeclarationAt(tree, line, column, uri)
    }

    // TODO: Context-unaware. Cannot provide member completion after '.' or
    //       type-aware suggestions. Needs compiler TypeResolver (Phase 5).
    override fun getCompletions(
        uri: String,
        line: Int,
        column: Int,
    ): List<CompletionItem> =
        buildList {
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
        }

    // TODO: Same-file only. Cross-file requires compiler's NameResolver (Phase 4).
    //       Cannot resolve: imports, inherited members, overloaded methods
    override fun findDefinition(
        uri: String,
        line: Int,
        column: Int,
    ): Location? {
        val (tree, name) = getIdentifierAt(uri, line, column, "definition") ?: return null

        val symbols = queryEngine.findAllDeclarations(tree, uri)
        val decl = symbols.find { it.name == name }

        return decl?.location?.also { loc ->
            logger.info("$logPrefix definition '{}' -> {}:{}", name, loc.startLine, loc.startColumn)
        } ?: run {
            logger.info(
                "$logPrefix definition '{}' not found ({} symbols: {})",
                name,
                symbols.size,
                symbols.take(5).joinToString { it.name },
            )
            null
        }
    }

    // TODO: Same-file text matching only. Cannot distinguish shadowed locals.
    //       Cross-file references require compiler's semantic model (Phase 4+).
    override fun findReferences(
        uri: String,
        line: Int,
        column: Int,
        includeDeclaration: Boolean,
    ): List<Location> {
        val (tree, name) = getIdentifierAt(uri, line, column, "references") ?: return emptyList()
        return queryEngine.findAllIdentifiers(tree, name, uri).also {
            logger.info("$logPrefix references '{}' -> {} found", name, it.size)
        }
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

    // TODO LSP: Wire this up in XtcLanguageServer.didClose()

    /**
     * Close and release resources for a document.
     * Called by the language server when a document is closed.
     */
    @Suppress("unused")
    fun closeDocument(uri: String) {
        parsedTrees.remove(uri)?.close()
        compilationResults.remove(uri)
    }

    override fun close() {
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
