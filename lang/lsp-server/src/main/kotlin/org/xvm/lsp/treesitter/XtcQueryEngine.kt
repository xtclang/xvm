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

    /**
     * Find all declarations in the tree for document symbols.
     */
    fun findAllDeclarations(
        tree: XtcTree,
        uri: String,
    ): List<SymbolInfo> {
        logger.info("[QueryEngine] findAllDeclarations: uri={}", uri.substringAfterLast('/'))
        return buildList {
            executeQuery(allDeclarationsQuery, tree) { captures ->
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
        }.also { logger.info("[QueryEngine] findAllDeclarations -> {} symbols", it.size) }
    }

    /**
     * Find all method declarations.
     */
    fun findMethodDeclarations(
        tree: XtcTree,
        uri: String,
    ): List<SymbolInfo> {
        logger.info("[QueryEngine] findMethodDeclarations: uri={}", uri.substringAfterLast('/'))
        return buildList {
            executeQuery(methodDeclarationsQuery, tree) { captures ->
                val name = captures["name"]?.text ?: return@executeQuery
                val declaration = captures["declaration"] ?: return@executeQuery
                add(declaration.toSymbolInfo(name, SymbolKind.METHOD, uri))
            }
        }.also { logger.info("[QueryEngine] findMethodDeclarations -> {} methods", it.size) }
    }

    /**
     * Find all identifiers with a given name (for find references).
     */
    fun findAllIdentifiers(
        tree: XtcTree,
        name: String,
        uri: String,
    ): List<Location> {
        logger.info("[QueryEngine] findAllIdentifiers: name='{}', uri={}", name, uri.substringAfterLast('/'))
        return buildList {
            executeQuery(identifiersQuery, tree) { captures ->
                val id = captures["id"] ?: return@executeQuery
                if (id.text == name) {
                    add(id.toLocation(uri))
                }
            }
        }.also { matches ->
            logger.info("[QueryEngine] findAllIdentifiers '{}' -> {} match(es)", name, matches.size)
            if (matches.isNotEmpty()) {
                matches.forEach { loc ->
                    logger.info("[QueryEngine]   {}:{}:{}", loc.uri.substringAfterLast('/'), loc.startLine + 1, loc.startColumn + 1)
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
     * Find the declaration containing a given position.
     */
    fun findDeclarationAt(
        tree: XtcTree,
        line: Int,
        column: Int,
        uri: String,
    ): SymbolInfo? {
        logger.info("[QueryEngine] findDeclarationAt: {}:{} in {}", line, column, uri.substringAfterLast('/'))
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
                    logger.info("[QueryEngine] findDeclarationAt -> '{}' ({})", it.name, kind)
                }
            }
            current = current.parent
        }
        logger.info("[QueryEngine] findDeclarationAt -> null (no enclosing declaration)")
        return null
    }

    private fun nodeTypeToSymbolKind(type: String): SymbolKind? =
        when (type) {
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
        logger.info("[QueryEngine] findImports")
        return buildList {
            executeQuery(importsQuery, tree) { captures ->
                val importNode = captures["import"] ?: return@executeQuery
                add(importNode.text)
            }
        }.also { logger.info("[QueryEngine] findImports -> {} imports", it.size) }
    }

    /**
     * Find imports in the tree with their source locations.
     */
    fun findImportLocations(
        tree: XtcTree,
        uri: String,
    ): List<Pair<String, Location>> {
        logger.info("[QueryEngine] findImportLocations: uri={}", uri.substringAfterLast('/'))
        return buildList {
            executeQuery(importsQuery, tree) { captures ->
                val importNode = captures["import"] ?: return@executeQuery
                add(importNode.text to importNode.toLocation(uri))
            }
        }.also { logger.info("[QueryEngine] findImportLocations -> {} imports", it.size) }
    }

    private fun executeQuery(
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
        logger.info("[QueryEngine] executeQuery: {} pattern(s), {} match(es)", query.patternCount, matchCount)
    }

    override fun close() {
        allDeclarationsQuery.close()
        methodDeclarationsQuery.close()
        identifiersQuery.close()
        importsQuery.close()
    }
}
