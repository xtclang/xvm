package org.xvm.lsp.adapter.xdk

import org.xvm.compiler.Source
import org.xvm.compiler.ast.AstNode
import org.xvm.compiler.ast.IncompleteDeclarationStatement
import org.xvm.compiler.ast.MethodDeclarationStatement
import org.xvm.compiler.ast.PropertyDeclarationStatement
import org.xvm.compiler.ast.TypeCompositionStatement
import org.xvm.lsp.model.Location
import org.xvm.lsp.model.SymbolInfo
import org.xvm.lsp.model.SymbolInfo.SymbolKind

/**
 * Turning parsed source into symbols.
 *
 * Symbols have to come from the AST rather than from the compiled structures, which is not
 * obvious: a `ClassStructure` knows its name, its kind, its members and its type, and knows
 * nothing whatever about the text it was written in. It carries no source position. An editor
 * cannot use a symbol it cannot point at, so the structures - the richer, resolved side of the
 * compiler - are the wrong source for this, and the AST is the right one.
 *
 * What that costs: an AST node knows what was written, not what it resolved to. Names here are as
 * they appear in the source, not qualified or resolved, and nothing below a declaration is
 * followed. That is enough for `documentSymbol` and for finding what the cursor is inside, and it
 * is not enough for go-to-definition or completion, which need resolution rather than syntax.
 */
internal object XdkSymbols {
    /**
     * @param uri  the document the AST was parsed from
     * @param root the parsed source, or null if it did not parse
     *
     * @return the declarations it contains, nested as they are nested in the source
     */
    fun of(
        uri: String,
        root: AstNode?,
    ): List<SymbolInfo> = root?.let { declarationsIn(uri, it, it.source) } ?: emptyList()

    /**
     * Find the innermost declaration containing a position, which is what a request about "the
     * thing the cursor is on" means when all we have is the shape of the source.
     */
    fun at(
        symbols: List<SymbolInfo>,
        line: Int,
        column: Int,
    ): SymbolInfo? =
        symbols.firstOrNull { contains(it.location, line, column) }?.let { outer ->
            at(outer.children, line, column) ?: outer
        }

    private fun contains(
        where: Location,
        line: Int,
        column: Int,
    ): Boolean {
        if (line < where.startLine || line > where.endLine) {
            return false
        }
        if (line == where.startLine && column < where.startColumn) {
            return false
        }
        return !(line == where.endLine && column > where.endColumn)
    }

    /**
     * Walk the children of a node, collecting the declarations and recursing through everything
     * else - a class sits inside a statement block inside a module, and only the declarations are
     * worth reporting.
     */
    private fun declarationsIn(
        uri: String,
        node: AstNode,
        source: Source?,
    ): List<SymbolInfo> =
        buildList {
            node.children().forEach { child ->
                // Recovered syntax has no compilation parentage yet; an absent source inherits the
                // enclosing syntax tree's source. An explicitly different source is a module member.
                if (child.source != null && child.source !== source) return@forEach
                val symbol = symbolOf(uri, child)
                if (symbol == null) {
                    addAll(declarationsIn(uri, child, source))
                } else {
                    add(symbol.withChildren(declarationsIn(uri, child, source)))
                }
            }
        }

    private fun symbolOf(
        uri: String,
        node: AstNode,
    ): SymbolInfo? =
        when (node) {
            is TypeCompositionStatement -> {
                node.name?.let { SymbolInfo.of(it, kindOf(node), rangeOf(uri, node)) }
            }

            is MethodDeclarationStatement -> {
                SymbolInfo.of(node.name, SymbolKind.METHOD, rangeOf(uri, node))
            }

            is PropertyDeclarationStatement -> {
                SymbolInfo.of(node.name, SymbolKind.PROPERTY, rangeOf(uri, node))
            }

            is IncompleteDeclarationStatement -> {
                node.nameToken.orElse(null)?.let {
                    val kind = if (node.kind == IncompleteDeclarationStatement.Kind.METHOD) SymbolKind.METHOD else SymbolKind.PROPERTY
                    SymbolInfo.of(it.valueText, kind, rangeOf(uri, node))
                }
            }

            else -> {
                null
            }
        }

    /**
     * The category token is what the author wrote - `class`, `mixin`, `service` - so it is what
     * the outline should say.
     */
    private fun kindOf(node: TypeCompositionStatement): SymbolKind =
        when (
            node.category.id.TEXT
                ?.lowercase()
        ) {
            "module" -> SymbolKind.MODULE
            "package" -> SymbolKind.PACKAGE
            "interface" -> SymbolKind.INTERFACE
            "mixin", "annotation" -> SymbolKind.MIXIN
            "service" -> SymbolKind.SERVICE
            "const" -> SymbolKind.CONST
            "enum" -> SymbolKind.ENUM
            else -> SymbolKind.CLASS
        }

    private fun rangeOf(
        uri: String,
        node: AstNode,
    ): Location =
        Location(
            uri = uri,
            startLine = Source.calculateLine(node.startPosition),
            startColumn = Source.calculateOffset(node.startPosition),
            endLine = Source.calculateLine(node.endPosition),
            endColumn = Source.calculateOffset(node.endPosition),
        )
}
