package org.xvm.lsp.adapter.xdk

import org.xvm.asm.XvmStructure
import org.xvm.compiler.Source
import org.xvm.compiler.ast.AstNode
import org.xvm.compiler.ast.IncompleteDeclarationStatement
import org.xvm.compiler.ast.MethodDeclarationStatement
import org.xvm.compiler.ast.PropertyDeclarationStatement
import org.xvm.compiler.ast.StatementBlock
import org.xvm.compiler.ast.TypeCompositionStatement
import org.xvm.compiler.ast.TypedefStatement
import org.xvm.lsp.adapter.FoldingRange
import org.xvm.lsp.adapter.Position
import org.xvm.lsp.adapter.Range
import org.xvm.lsp.model.Location
import java.util.IdentityHashMap

/**
 * Reading a compiled document's AST for the questions an editor asks about a position.
 *
 * The AST exposes children and source spans for folding and selection. Semantic queries use
 * the compiler-owned snapshot cached by [XdkAdapter].
 */
internal object XdkAst {
    /** Attempt-local structure ownership; binary structures cannot acquire a guessed source span. */
    fun declarationLocations(
        roots: Collection<AstNode>,
        sourceUris: Map<String, String>,
    ): Map<XvmStructure, Location> {
        val locations = IdentityHashMap<XvmStructure, Location>()

        fun visit(node: AstNode) {
            val declaration =
                when (node) {
                    is TypeCompositionStatement -> node.component to node.nameToken
                    is MethodDeclarationStatement -> node.component to node.nameToken
                    is PropertyDeclarationStatement -> node.component to node.nameToken
                    is TypedefStatement -> node.component to node.nameToken
                    else -> null
                }
            val uri = node.source?.fileName?.let(sourceUris::get)
            if (declaration != null && uri != null) {
                val (structure, token) = declaration
                if (structure != null && token != null) {
                    val range = spanOf(token.startPosition, token.endPosition)
                    locations[structure] = Location(uri, range.start.line, range.start.column, range.end.line, range.end.column)
                    locations[structure.identityConstant] = locations.getValue(structure)
                }
            }
            node.childNodes().forEach(::visit)
        }
        roots.forEach(::visit)
        return locations
    }

    /** Each source keeps its own structural root even when module assembly nests the trees. */
    fun rootsBySource(root: AstNode?): Map<String, AstNode> =
        buildMap {
            fun visit(node: AstNode) {
                node.source?.fileName?.let { putIfAbsent(it, node) }
                node.childNodes().forEach(::visit)
            }
            root?.let(::visit)
        }

    /**
     * The chain of nodes containing a position, outermost first, innermost last.
     *
     * Empty if the position is outside the document or the document did not parse.
     */
    fun chainAt(
        root: AstNode?,
        line: Int,
        column: Int,
    ): List<AstNode> {
        val chain = mutableListOf<AstNode>()
        var node = root ?: return chain
        if (!node.contains(line, column)) {
            return chain
        }
        while (true) {
            chain += node
            // the AST nests, so at most one child can contain a position - except where two
            // siblings share a boundary, and then the first one that does is as good an answer
            val next = node.childList().firstOrNull { it.belongsTo(root) && it.contains(line, column) } ?: return chain
            node = next
        }
    }

    /**
     * The regions worth collapsing: anything that spans more than one line and is a block or a
     * declaration. An editor offers a fold per region, so a region per expression would be noise.
     */
    fun foldingRegions(root: AstNode?): List<FoldingRange> {
        val found = linkedMapOf<Pair<Int, Int>, FoldingRange>()
        val lines =
            root
                ?.source
                ?.toRawString()
                ?.lines()
                .orEmpty()

        fun walk(node: AstNode) {
            if (root != null && !node.belongsTo(root)) return
            if (node is StatementBlock || node is TypeCompositionStatement || node is IncompleteDeclarationStatement) {
                val start = lineOf(node.startPosition)
                val end = lineOf(node.endPosition)
                if (end > start) {
                    val column = columnOf(node.endPosition)
                    // Keep the heading line and a real closing brace visible. An unfinished
                    // region ends at its actual source boundary; never invent a delimiter.
                    val endCharacter = if (lines.getOrNull(end)?.getOrNull(column - 1) == '}') column - 1 else column
                    found.putIfAbsent(start to end, FoldingRange(start, end, endCharacter = endCharacter))
                }
            }
            node.childList().forEach(::walk)
        }
        root?.let(::walk)
        return found.values.toList()
    }

    fun rangeOf(node: AstNode): Range = spanOf(node.startPosition, node.endPosition)

    fun spanOf(
        start: Long,
        end: Long,
    ): Range =
        Range(
            start = Position(lineOf(start), columnOf(start)),
            end = Position(lineOf(end), columnOf(end)),
        )

    private fun lineOf(position: Long): Int = Source.calculateLine(position)

    private fun columnOf(position: Long): Int = Source.calculateOffset(position)

    // Parent pointers are installed during compilation. Before that, children without their own
    // Source inherit the source of the syntax tree being traversed.
    private fun AstNode.belongsTo(root: AstNode): Boolean = source == null || source === root.source

    /**
     * [AstNode.children] is an iterator that supports replacement during a compiler pass; a
     * reader wants a list, taken once.
     */
    private fun AstNode.childList(): List<AstNode> = childNodes().toList()

    private fun AstNode.contains(
        line: Int,
        column: Int,
    ): Boolean {
        val startLine = lineOf(startPosition)
        val endLine = lineOf(endPosition)
        if (line < startLine || line > endLine) {
            return false
        }
        if (line == startLine && column < columnOf(startPosition)) {
            return false
        }
        return !(line == endLine && column > columnOf(endPosition))
    }
}
