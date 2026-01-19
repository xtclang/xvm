package org.xvm.lsp.treesitter

import io.github.treesitter.jtreesitter.Language
import io.github.treesitter.jtreesitter.Query
import io.github.treesitter.jtreesitter.QueryCursor
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
class XtcQueryEngine(private val language: Language) : Closeable {

    private val allDeclarationsQuery: Query = Query(language, XtcQueries.ALL_DECLARATIONS)
    private val typeDeclarationsQuery: Query = Query(language, XtcQueries.TYPE_DECLARATIONS)
    private val methodDeclarationsQuery: Query = Query(language, XtcQueries.METHOD_DECLARATIONS)
    private val propertyDeclarationsQuery: Query = Query(language, XtcQueries.PROPERTY_DECLARATIONS)
    private val identifiersQuery: Query = Query(language, XtcQueries.IDENTIFIERS)
    private val variableDeclarationsQuery: Query = Query(language, XtcQueries.VARIABLE_DECLARATIONS)
    private val importsQuery: Query = Query(language, XtcQueries.IMPORTS)

    /**
     * Find all declarations in the tree for document symbols.
     */
    fun findAllDeclarations(tree: XtcTree, uri: String): List<SymbolInfo> {
        val symbols = mutableListOf<SymbolInfo>()

        executeQuery(allDeclarationsQuery, tree) { captures ->
            val name = captures["name"]?.text ?: return@executeQuery
            val declaration = captures.entries.find { (key, _) ->
                key in listOf("module", "package", "class", "interface", "mixin",
                    "service", "const", "enum", "method", "constructor", "property")
            } ?: return@executeQuery

            val kind = when (declaration.key) {
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
            symbols.add(
                SymbolInfo(
                    name = name,
                    qualifiedName = name,
                    kind = kind,
                    location = Location(
                        uri = uri,
                        startLine = node.startLine,
                        startColumn = node.startColumn,
                        endLine = node.endLine,
                        endColumn = node.endColumn
                    )
                )
            )
        }

        return symbols
    }

    /**
     * Find all type declarations (classes, interfaces, etc.).
     */
    fun findTypeDeclarations(tree: XtcTree, uri: String): List<SymbolInfo> {
        val symbols = mutableListOf<SymbolInfo>()

        executeQuery(typeDeclarationsQuery, tree) { captures ->
            val name = captures["name"]?.text ?: return@executeQuery
            val declaration = captures["declaration"] ?: return@executeQuery
            val kind = nodeTypeToSymbolKind(declaration.type) ?: SymbolKind.CLASS

            symbols.add(declaration.toSymbolInfo(name, kind, uri))
        }

        return symbols
    }

    /**
     * Find all method declarations.
     */
    fun findMethodDeclarations(tree: XtcTree, uri: String): List<SymbolInfo> {
        val symbols = mutableListOf<SymbolInfo>()

        executeQuery(methodDeclarationsQuery, tree) { captures ->
            val name = captures["name"]?.text ?: return@executeQuery
            val declaration = captures["declaration"] ?: return@executeQuery
            symbols.add(declaration.toSymbolInfo(name, SymbolKind.METHOD, uri))
        }

        return symbols
    }

    /**
     * Find all property declarations.
     */
    fun findPropertyDeclarations(tree: XtcTree, uri: String): List<SymbolInfo> {
        val symbols = mutableListOf<SymbolInfo>()

        executeQuery(propertyDeclarationsQuery, tree) { captures ->
            val name = captures["name"]?.text ?: return@executeQuery
            val type = captures["type"]?.text
            val declaration = captures["declaration"] ?: return@executeQuery
            symbols.add(declaration.toSymbolInfo(name, SymbolKind.PROPERTY, uri, type?.let { "$it $name" }))
        }

        return symbols
    }

    /**
     * Find all identifiers with a given name (for find references).
     */
    fun findAllIdentifiers(tree: XtcTree, name: String, uri: String): List<Location> {
        val locations = mutableListOf<Location>()

        executeQuery(identifiersQuery, tree) { captures ->
            val id = captures["id"] ?: return@executeQuery
            if (id.text == name) {
                locations.add(id.toLocation(uri))
            }
        }

        return locations
    }

    private fun XtcNode.toLocation(uri: String) = Location(
        uri = uri,
        startLine = startLine,
        startColumn = startColumn,
        endLine = endLine,
        endColumn = endColumn
    )

    /**
     * Find the declaration containing a given position.
     */
    fun findDeclarationAt(tree: XtcTree, line: Int, column: Int, uri: String): SymbolInfo? {
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

    private fun nodeTypeToSymbolKind(type: String): SymbolKind? = when (type) {
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
        typeSignature: String? = null
    ) = SymbolInfo(
        name = name,
        qualifiedName = name,
        kind = kind,
        location = toLocation(uri),
        typeSignature = typeSignature
    )

    /**
     * Find imports in the tree.
     */
    fun findImports(tree: XtcTree): List<String> {
        val imports = mutableListOf<String>()

        executeQuery(importsQuery, tree) { captures ->
            val importNode = captures["import"] ?: return@executeQuery
            imports.add(importNode.text)
        }

        return imports
    }

    private fun executeQuery(query: Query, tree: XtcTree, handler: (Map<String, XtcNode>) -> Unit) {
        QueryCursor(query).use { cursor ->
            cursor.findMatches(tree.tsTree.rootNode).forEach { match ->
                val captures = mutableMapOf<String, XtcNode>()

                for (capture in match.captures()) {
                    captures[capture.name()] = XtcNode(capture.node(), tree.source)
                }

                handler(captures)
            }
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
