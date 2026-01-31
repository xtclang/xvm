package org.xvm.lsp.adapter

import org.slf4j.LoggerFactory
import org.xvm.lsp.adapter.XtcCompilerAdapter.CompletionItem
import org.xvm.lsp.adapter.XtcCompilerAdapter.CompletionItem.CompletionKind
import org.xvm.lsp.model.CompilationResult
import org.xvm.lsp.model.Diagnostic
import org.xvm.lsp.model.Location
import org.xvm.lsp.model.SymbolInfo
import org.xvm.lsp.model.SymbolInfo.SymbolKind
import org.xvm.lsp.treesitter.XtcNode
import org.xvm.lsp.treesitter.XtcParser
import org.xvm.lsp.treesitter.XtcQueryEngine
import org.xvm.lsp.treesitter.XtcTree
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import kotlin.system.measureNanoTime

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
class TreeSitterAdapter :
    XtcCompilerAdapter,
    Closeable {
    override val displayName: String = "Tree-sitter (syntax-aware)"

    private val parser: XtcParser = XtcParser()
    private val queryEngine: XtcQueryEngine = XtcQueryEngine(parser.getLanguage())
    private val parsedTrees = ConcurrentHashMap<String, XtcTree>()
    private val compilationResults = ConcurrentHashMap<String, CompilationResult>()

    companion object {
        private val logger = LoggerFactory.getLogger(TreeSitterAdapter::class.java)

        /**
         * Execute a block and return its result along with elapsed time in milliseconds (with sub-ms precision).
         */
        private inline fun <T> timed(block: () -> T): Pair<T, Double> {
            var result: T
            val nanos = measureNanoTime { result = block() }
            return result to (nanos / 1_000_000.0)
        }

        // XTC keywords for completion
        private val KEYWORDS =
            listOf(
                "module",
                "package",
                "import",
                "as",
                "class",
                "interface",
                "mixin",
                "service",
                "const",
                "enum",
                "public",
                "private",
                "protected",
                "static",
                "abstract",
                "extends",
                "implements",
                "incorporates",
                "delegates",
                "into",
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
                "is",
                "as",
                "val",
                "var",
                "construct",
                "function",
                "typedef",
                "true",
                "false",
                "null",
            )

        // Built-in types for completion
        private val BUILT_IN_TYPES =
            listOf(
                "Int",
                "Int8",
                "Int16",
                "Int32",
                "Int64",
                "Int128",
                "IntN",
                "UInt",
                "UInt8",
                "UInt16",
                "UInt32",
                "UInt64",
                "UInt128",
                "UIntN",
                "Dec",
                "Dec32",
                "Dec64",
                "Dec128",
                "DecN",
                "Float",
                "Float16",
                "Float32",
                "Float64",
                "Float128",
                "FloatN",
                "String",
                "Char",
                "Boolean",
                "Bit",
                "Byte",
                "Object",
                "Enum",
                "Exception",
                "Const",
                "Service",
                "Module",
                "Package",
                "Array",
                "List",
                "Set",
                "Map",
                "Range",
                "Interval",
                "Tuple",
                "Function",
                "Method",
                "Property",
                "Type",
                "Class",
                "Nullable",
                "Orderable",
                "Hashable",
                "Stringable",
                "Iterator",
                "Iterable",
                "Collection",
                "Sequence",
                "void",
                "Null",
                "True",
                "False",
            )
    }

    override fun compile(
        uri: String,
        content: String,
    ): CompilationResult {
        logger.info("TreeSitterAdapter: parsing {} ({} bytes)", uri, content.length)

        // Parse the content (with incremental parsing if we have an old tree)
        val oldTree = parsedTrees[uri]
        val isIncremental = oldTree != null

        val (tree, parseElapsed) =
            try {
                timed { parser.parse(content, oldTree) }
            } catch (e: Exception) {
                logger.error("TreeSitterAdapter: parse failed for {}: {}", uri, e.message)
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
            "TreeSitterAdapter: parsed in {:.1f}ms ({}), {} errors, {} symbols (query: {:.1f}ms)",
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

    override fun getHoverInfo(
        uri: String,
        line: Int,
        column: Int,
    ): String? {
        val symbol = findSymbolAt(uri, line, column) ?: return null

        return buildString {
            append("```xtc\n")
            if (symbol.typeSignature != null) {
                append(symbol.typeSignature)
            } else {
                append(symbol.kind.name.lowercase())
                append(" ")
                append(symbol.name)
            }
            append("\n```")
            if (symbol.documentation != null) {
                append("\n\n")
                append(symbol.documentation)
            }
        }
    }

    override fun getCompletions(
        uri: String,
        line: Int,
        column: Int,
    ): List<CompletionItem> =
        buildList {
            // Add keywords
            KEYWORDS.forEach { keyword ->
                add(
                    CompletionItem(
                        label = keyword,
                        kind = CompletionKind.KEYWORD,
                        detail = "keyword",
                        insertText = keyword,
                    ),
                )
            }

            // Add built-in types
            BUILT_IN_TYPES.forEach { type ->
                add(
                    CompletionItem(
                        label = type,
                        kind = CompletionKind.CLASS,
                        detail = "built-in type",
                        insertText = type,
                    ),
                )
            }

            // Add symbols from current document
            val tree = parsedTrees[uri]
            if (tree != null) {
                val symbols = queryEngine.findAllDeclarations(tree, uri)
                symbols.forEach { symbol ->
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

            // Add imports from current document
            tree?.let {
                queryEngine.findImports(it).forEach { importPath ->
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

    override fun findDefinition(
        uri: String,
        line: Int,
        column: Int,
    ): Location? {
        val tree = parsedTrees[uri] ?: return null
        val node = tree.nodeAt(line, column) ?: return null
        val identifierNode = findIdentifierNode(node) ?: return null

        // Search for declaration with this name
        val symbols = queryEngine.findAllDeclarations(tree, uri)
        val declaration = symbols.find { it.name == identifierNode.text }

        return declaration?.location
    }

    override fun findReferences(
        uri: String,
        line: Int,
        column: Int,
        includeDeclaration: Boolean,
    ): List<Location> {
        val tree = parsedTrees[uri] ?: return emptyList()
        val node = tree.nodeAt(line, column) ?: return emptyList()
        val identifierNode = findIdentifierNode(node) ?: return emptyList()

        // Find all occurrences of this identifier
        return queryEngine.findAllIdentifiers(tree, identifierNode.text, uri)
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

    private fun toCompletionKind(kind: SymbolKind): CompletionKind =
        when (kind) {
            SymbolKind.MODULE, SymbolKind.PACKAGE -> CompletionKind.MODULE
            SymbolKind.CLASS, SymbolKind.ENUM, SymbolKind.CONST,
            SymbolKind.MIXIN, SymbolKind.SERVICE,
            -> CompletionKind.CLASS
            SymbolKind.INTERFACE -> CompletionKind.INTERFACE
            SymbolKind.METHOD, SymbolKind.CONSTRUCTOR -> CompletionKind.METHOD
            SymbolKind.PROPERTY -> CompletionKind.PROPERTY
            SymbolKind.PARAMETER, SymbolKind.TYPE_PARAMETER -> CompletionKind.VARIABLE
        }
}
