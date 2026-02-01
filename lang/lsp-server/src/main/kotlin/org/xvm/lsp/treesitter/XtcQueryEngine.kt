package org.xvm.lsp.treesitter

import io.github.treesitter.jtreesitter.Language
import io.github.treesitter.jtreesitter.Query
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
class XtcQueryEngine(
    private val language: Language,
) : Closeable {
    // Note: In jtreesitter 0.24.x, Query constructor is package-private.
    // Use Language.query() factory method instead.
    private val allDeclarationsQuery: Query = language.query(XtcQueries.ALL_DECLARATIONS)
    private val typeDeclarationsQuery: Query = language.query(XtcQueries.TYPE_DECLARATIONS)
    private val methodDeclarationsQuery: Query = language.query(XtcQueries.METHOD_DECLARATIONS)
    private val propertyDeclarationsQuery: Query = language.query(XtcQueries.PROPERTY_DECLARATIONS)
    private val identifiersQuery: Query = language.query(XtcQueries.IDENTIFIERS)
    private val variableDeclarationsQuery: Query = language.query(XtcQueries.VARIABLE_DECLARATIONS)
    private val importsQuery: Query = language.query(XtcQueries.IMPORTS)

    /**
     * Find all declarations in the tree for document symbols.
     */
    fun findAllDeclarations(
        tree: XtcTree,
        uri: String,
    ): List<SymbolInfo> =
        buildList {
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
        }

    /**
     * Find all type declarations (classes, interfaces, etc.).
     */
    fun findTypeDeclarations(
        tree: XtcTree,
        uri: String,
    ): List<SymbolInfo> =
        buildList {
            executeQuery(typeDeclarationsQuery, tree) { captures ->
                val name = captures["name"]?.text ?: return@executeQuery
                val declaration = captures["declaration"] ?: return@executeQuery
                val kind = nodeTypeToSymbolKind(declaration.type) ?: SymbolKind.CLASS
                add(declaration.toSymbolInfo(name, kind, uri))
            }
        }

    /**
     * Find all method declarations.
     */
    fun findMethodDeclarations(
        tree: XtcTree,
        uri: String,
    ): List<SymbolInfo> =
        buildList {
            executeQuery(methodDeclarationsQuery, tree) { captures ->
                val name = captures["name"]?.text ?: return@executeQuery
                val declaration = captures["declaration"] ?: return@executeQuery
                add(declaration.toSymbolInfo(name, SymbolKind.METHOD, uri))
            }
        }

    /**
     * Find all property declarations.
     */
    fun findPropertyDeclarations(
        tree: XtcTree,
        uri: String,
    ): List<SymbolInfo> =
        buildList {
            executeQuery(propertyDeclarationsQuery, tree) { captures ->
                val name = captures["name"]?.text ?: return@executeQuery
                val type = captures["type"]?.text
                val declaration = captures["declaration"] ?: return@executeQuery
                add(declaration.toSymbolInfo(name, SymbolKind.PROPERTY, uri, type?.let { "$it $name" }))
            }
        }

    /**
     * Find all identifiers with a given name (for find references).
     */
    fun findAllIdentifiers(
        tree: XtcTree,
        name: String,
        uri: String,
    ): List<Location> =
        buildList {
            executeQuery(identifiersQuery, tree) { captures ->
                val id = captures["id"] ?: return@executeQuery
                if (id.text == name) {
                    add(id.toLocation(uri))
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
        val node = tree.nodeAt(line, column) ?: return null

        // Walk up to find enclosing declaration
        var current: XtcNode? = node
        while (current != null) {
            val kind = nodeTypeToSymbolKind(current.type)
            if (kind != null) {
                val nameNode = current.childByFieldName("name") ?: return null
                return current.toSymbolInfo(nameNode.text, kind, uri)
            }
            current = current.parent
        }
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
     * Find imports in the tree.
     */
    fun findImports(tree: XtcTree): List<String> =
        buildList {
            executeQuery(importsQuery, tree) { captures ->
                val importNode = captures["import"] ?: return@executeQuery
                add(importNode.text)
            }
        }

    private fun executeQuery(
        query: Query,
        tree: XtcTree,
        handler: (Map<String, XtcNode>) -> Unit,
    ) {
        // In jtreesitter 0.24.x, Query.findMatches() executes directly (no QueryCursor)
        query.findMatches(tree.tsTree.rootNode).forEach { match ->
            val captures =
                match.captures().associate { capture ->
                    capture.name() to XtcNode(capture.node(), tree.source)
                }
            handler(captures)
        }
    }

    override fun close() {
        allDeclarationsQuery.close()
        typeDeclarationsQuery.close()
        methodDeclarationsQuery.close()
        propertyDeclarationsQuery.close()
        identifiersQuery.close()
        variableDeclarationsQuery.close()
        importsQuery.close()
    }
}
