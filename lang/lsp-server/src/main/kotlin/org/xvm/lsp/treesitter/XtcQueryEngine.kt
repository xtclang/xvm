package org.xvm.lsp.treesitter

import io.github.treesitter.jtreesitter.Language
import io.github.treesitter.jtreesitter.Query
import io.github.treesitter.jtreesitter.QueryCursor
import org.slf4j.LoggerFactory
import org.xvm.lsp.model.Location
import org.xvm.lsp.model.SymbolInfo
import org.xvm.lsp.model.SymbolInfo.SymbolKind
import java.io.Closeable

/**
 * Query engine for extracting information from XTC syntax trees.
 *
 * Uses Tree-sitter queries to find declarations, references, and other
 * language constructs in parsed source code.
 */
@Suppress("LoggingSimilarMessage")
class XtcQueryEngine(
    language: Language,
) : Closeable {
    private val logger = LoggerFactory.getLogger(XtcQueryEngine::class.java)

    private val allDeclarationsQuery: Query = Query(language, XtcQueries.allDeclarations)
    private val methodDeclarationsQuery: Query = Query(language, XtcQueries.methodDeclarations)
    private val identifiersQuery: Query = Query(language, XtcQueries.identifiers)
    private val importsQuery: Query = Query(language, XtcQueries.imports)
    private val commentsAndStringsQuery: Query = Query(language, XtcQueries.commentsAndStrings)

    /**
     * Find all declarations in the tree for document symbols.
     */
    fun findAllDeclarations(
        tree: XtcTree,
        uri: String,
    ): List<SymbolInfo> {
        logger.info("findAllDeclarations: uri={}", uri.substringAfterLast('/'))
        return buildList {
            executeQuery("allDeclarations", allDeclarationsQuery, tree) { captures ->
                val name = captures["name"]?.text ?: return@executeQuery
                val declaration =
                    captures.entries.find { (key, _) ->
                        key in
                            listOf(
                                "module",
                                "package",
                                "class",
                                "interface",
                                "mixin",
                                "service",
                                "const",
                                "enum",
                                "method",
                                "constructor",
                                "property",
                            )
                    } ?: return@executeQuery

                val kind =
                    when (declaration.key) {
                        "module" -> SymbolKind.MODULE
                        "package" -> SymbolKind.PACKAGE
                        "class" -> SymbolKind.CLASS
                        "interface" -> SymbolKind.INTERFACE
                        "mixin" -> SymbolKind.MIXIN
                        "service" -> SymbolKind.SERVICE
                        "const" -> SymbolKind.CONST
                        "enum" -> SymbolKind.ENUM
                        "method" -> SymbolKind.METHOD
                        "constructor" -> SymbolKind.CONSTRUCTOR
                        "property" -> SymbolKind.PROPERTY
                        else -> return@executeQuery
                    }

                val node = declaration.value
                add(
                    SymbolInfo(
                        name = name,
                        qualifiedName = name,
                        kind = kind,
                        location =
                            Location(
                                uri = uri,
                                startLine = node.startLine,
                                startColumn = node.startColumn,
                                endLine = node.endLine,
                                endColumn = node.endColumn,
                            ),
                    ),
                )
            }
        }.also { symbols ->
            logger.info("findAllDeclarations -> {} symbols", symbols.size)
            if (symbols.isNotEmpty()) {
                symbols.forEach { s ->
                    logger.info(
                        "  {} '{}' at {}:{}:{}",
                        s.kind,
                        s.name,
                        s.location.uri.substringAfterLast('/'),
                        s.location.startLine + 1,
                        s.location.startColumn + 1,
                    )
                }
            }
        }
    }

    /**
     * Find all method declarations.
     */
    fun findMethodDeclarations(
        tree: XtcTree,
        uri: String,
    ): List<SymbolInfo> {
        logger.info("findMethodDeclarations: uri={}", uri.substringAfterLast('/'))
        return buildList {
            executeQuery("methodDeclarations", methodDeclarationsQuery, tree) { captures ->
                val name = captures["name"]?.text ?: return@executeQuery
                val declaration = captures["declaration"] ?: return@executeQuery
                add(declaration.toSymbolInfo(name, SymbolKind.METHOD, uri))
            }
        }.also { methods ->
            logger.info("findMethodDeclarations -> {} methods", methods.size)
            if (methods.isNotEmpty()) {
                methods.forEach { m ->
                    logger.info(
                        "  '{}' at {}:{}:{}",
                        m.name,
                        m.location.uri.substringAfterLast('/'),
                        m.location.startLine + 1,
                        m.location.startColumn + 1,
                    )
                }
            }
        }
    }

    /**
     * Find all identifiers with a given name (for find references).
     */
    fun findAllIdentifiers(
        tree: XtcTree,
        name: String,
        uri: String,
    ): List<Location> {
        logger.info("findAllIdentifiers: name='{}', uri={}", name, uri.substringAfterLast('/'))
        return buildList {
            executeQuery("identifiers", identifiersQuery, tree) { captures ->
                val id = captures["id"] ?: return@executeQuery
                if (id.text == name) {
                    add(id.toLocation(uri))
                }
            }
        }.also { matches ->
            logger.info("findAllIdentifiers '{}' -> {} match(es)", name, matches.size)
            if (matches.isNotEmpty()) {
                matches.forEach { loc ->
                    logger.info("  {}:{}:{}", loc.uri.substringAfterLast('/'), loc.startLine + 1, loc.startColumn + 1)
                }
            }
        }
    }

    private fun XtcNode.toLocation(uri: String) =
        Location(
            uri = uri,
            startLine = startLine,
            startColumn = startColumn,
            endLine = endLine,
            endColumn = endColumn,
        )

    /**
     * Resolve a name reference at the given cursor position to the nearest in-scope declaration.
     *
     * Walks the AST upward from the cursor and, for each enclosing scope, asks whether that
     * scope declares anything matching the requested name:
     *  - A `block` (function body, control-flow body) contributes local variables declared
     *    *before* the cursor position. Forward references are not allowed for locals.
     *  - A `method_declaration` / `function_declaration` contributes its parameters.
     *  - A `class_body` / `module_body` / `package_body` / `interface_body` / `enum_body` /
     *    `mixin_body` / `service_body` / `const_body` contributes its declared members
     *    (properties, methods, getters, nested types). Class/module members are visible
     *    throughout the body, so no before-cursor restriction applies.
     *
     * The first matching declaration in the enclosing chain wins, modelling shadowing. The
     * returned [Location] points at the declaration's `name` field (the identifier), so
     * cmd-click lands on the name itself rather than the head of the statement.
     *
     * Returns `null` if no enclosing scope declares the name. Callers should then consult
     * the workspace symbol index for a cross-file fallback.
     */
    fun resolveByNameInScope(
        tree: XtcTree,
        line: Int,
        column: Int,
        name: String,
        uri: String,
    ): Location? {
        val cursor = tree.nodeAt(line, column) ?: return null
        var current: XtcNode? = cursor
        while (current != null) {
            val match =
                when (current.type) {
                    "block" -> {
                        findLocalVariableInBlock(current, name, line, column)
                    }

                    "method_declaration", "function_declaration", "constructor_declaration" -> {
                        findParameterInMethod(current, name)
                    }

                    "class_body", "module_body", "package_body", "interface_body",
                    "enum_body", "mixin_body", "service_body", "const_body",
                    -> {
                        findMemberInBody(current, name)
                    }

                    else -> {
                        null
                    }
                }
            if (match != null) {
                logger.info(
                    "resolveByNameInScope '{}' -> {}:{}:{} (scope={})",
                    name,
                    uri.substringAfterLast('/'),
                    match.startLine + 1,
                    match.startColumn + 1,
                    current.type,
                )
                return match.toLocation(uri)
            }
            current = current.parent
        }
        return null
    }

    /**
     * Search a function/control-flow `block` for a `variable_declaration` whose name field
     * matches and whose declaration position precedes the cursor. Forward references aren't
     * legal Ecstasy, so a declaration at or after the cursor is ignored.
     */
    private fun findLocalVariableInBlock(
        block: XtcNode,
        name: String,
        cursorLine: Int,
        cursorColumn: Int,
    ): XtcNode? =
        block.children
            .asSequence()
            .filter { it.type == "variable_declaration" }
            .filter {
                it.endLine < cursorLine ||
                    (it.endLine == cursorLine && it.endColumn <= cursorColumn)
            }.mapNotNull { it.childByFieldName("name") }
            .firstOrNull { it.text == name }

    /**
     * Find a `parameter` node whose name field matches in the `parameters` child of a
     * method/function/constructor declaration.
     */
    private fun findParameterInMethod(
        method: XtcNode,
        name: String,
    ): XtcNode? {
        val params = method.childByFieldName("parameters") ?: return null
        return params.children
            .asSequence()
            .filter { it.type == "parameter" }
            .mapNotNull { it.childByFieldName("name") }
            .firstOrNull { it.text == name }
    }

    /**
     * Search a class/module/package/interface/enum/mixin/service/const body for a declared
     * member (property, method, getter, nested type) whose name matches. Members are visible
     * throughout the body, so position is not constrained.
     */
    private fun findMemberInBody(
        body: XtcNode,
        name: String,
    ): XtcNode? =
        body.children
            .asSequence()
            .filter { it.type in memberNodeTypes }
            .mapNotNull { it.childByFieldName("name") }
            .firstOrNull { it.text == name }

    private companion object {
        private val memberNodeTypes =
            setOf(
                "property_declaration",
                "property_getter_declaration",
                "method_declaration",
                "constructor_declaration",
                "class_declaration",
                "interface_declaration",
                "mixin_declaration",
                "service_declaration",
                "const_declaration",
                "enum_declaration",
                "annotation_declaration",
                "package_declaration",
                "typedef_declaration",
            )
    }

    /**
     * Find the declaration containing a given position.
     */
    fun findDeclarationAt(
        tree: XtcTree,
        line: Int,
        column: Int,
        uri: String,
    ): SymbolInfo? {
        logger.info("findDeclarationAt: {}:{} in {}", line, column, uri.substringAfterLast('/'))
        val node = tree.nodeAt(line, column) ?: return null

        // Walk up to find enclosing declaration
        var current: XtcNode? = node
        while (current != null) {
            val kind = nodeTypeToSymbolKind(current.type)
            if (kind != null) {
                // Use the 'name' field to find the declaration's name node.
                // Falls back to childByType for nodes without field definitions.
                val nameNode =
                    current.childByFieldName("name")
                        ?: current.childByType("identifier")
                        ?: current.childByType("type_name")
                        ?: return null
                return current.toSymbolInfo(nameNode.text, kind, uri).also {
                    logger.info("findDeclarationAt -> '{}' ({})", it.name, kind)
                }
            }
            current = current.parent
        }
        logger.info("findDeclarationAt -> null (no enclosing declaration)")
        return null
    }

    private fun nodeTypeToSymbolKind(type: String): SymbolKind? =
        when (type) {
            "module_declaration" -> SymbolKind.MODULE
            "package_declaration" -> SymbolKind.PACKAGE
            "class_declaration" -> SymbolKind.CLASS
            "interface_declaration" -> SymbolKind.INTERFACE
            "mixin_declaration" -> SymbolKind.MIXIN
            "service_declaration" -> SymbolKind.SERVICE
            "const_declaration" -> SymbolKind.CONST
            "enum_declaration" -> SymbolKind.ENUM
            "method_declaration" -> SymbolKind.METHOD
            "property_declaration", "variable_declaration" -> SymbolKind.PROPERTY
            else -> null
        }

    private fun XtcNode.toSymbolInfo(
        name: String,
        kind: SymbolKind,
        uri: String,
        typeSignature: String? = null,
    ) = SymbolInfo(
        name = name,
        qualifiedName = name,
        kind = kind,
        location = toLocation(uri),
        typeSignature = typeSignature,
    )

    /**
     * Find imports in the tree (text only).
     */
    fun findImports(tree: XtcTree): List<String> {
        logger.info("findImports")
        return buildList {
            executeQuery("imports", importsQuery, tree) { captures ->
                val importNode = captures["import"] ?: return@executeQuery
                add(importNode.text)
            }
        }.also { imports ->
            logger.info("findImports -> {} imports", imports.size)
            if (imports.isNotEmpty()) {
                imports.forEach { logger.info("  '{}'", it) }
            }
        }
    }

    /**
     * Find imports in the tree with their source locations.
     */
    fun findImportLocations(
        tree: XtcTree,
        uri: String,
    ): List<Pair<String, Location>> {
        logger.info("findImportLocations: uri={}", uri.substringAfterLast('/'))
        return buildList {
            executeQuery("imports", importsQuery, tree) { captures ->
                val importNode = captures["import"] ?: return@executeQuery
                add(importNode.text to importNode.toLocation(uri))
            }
        }.also { imports ->
            logger.info("findImportLocations -> {} imports", imports.size)
            if (imports.isNotEmpty()) {
                imports.forEach { (path, loc) ->
                    logger.info(
                        "  '{}' at {}:{}:{}",
                        path,
                        loc.uri.substringAfterLast('/'),
                        loc.startLine + 1,
                        loc.startColumn + 1,
                    )
                }
            }
        }
    }

    /**
     * Find all comment and string-literal nodes, returned as `(text, location)` pairs.
     *
     * These are the host nodes that may contain URLs, file paths, or other free-text
     * content the editor can hyperlink. Caller is responsible for scanning the text
     * and computing per-match ranges relative to the host node's start position.
     */
    fun findCommentAndStringNodes(
        tree: XtcTree,
        uri: String,
    ): List<Pair<String, Location>> {
        return buildList {
            executeQuery("commentsAndStrings", commentsAndStringsQuery, tree) { captures ->
                val node = captures["text"] ?: return@executeQuery
                add(node.text to node.toLocation(uri))
            }
        }.also { nodes ->
            logger.info("findCommentAndStringNodes -> {} nodes", nodes.size)
        }
    }

    private fun executeQuery(
        queryName: String,
        query: Query,
        tree: XtcTree,
        handler: (Map<String, XtcNode>) -> Unit,
    ) {
        var matchCount = 0
        QueryCursor(query).use { cursor ->
            cursor.findMatches(tree.tsTree.rootNode).forEach { match ->
                matchCount++
                val captures =
                    match.captures().associate { capture ->
                        capture.name() to XtcNode(capture.node(), tree.source)
                    }
                handler(captures)
            }
        }
        logger.info("executeQuery '{}': {} pattern(s), {} match(es)", queryName, query.patternCount, matchCount)
    }

    override fun close() {
        allDeclarationsQuery.close()
        methodDeclarationsQuery.close()
        identifiersQuery.close()
        importsQuery.close()
        commentsAndStringsQuery.close()
    }
}
