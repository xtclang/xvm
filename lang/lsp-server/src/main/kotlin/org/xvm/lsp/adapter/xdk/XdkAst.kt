package org.xvm.lsp.adapter.xdk

import org.xvm.compiler.Source
import org.xvm.compiler.ast.AstNode
import org.xvm.compiler.ast.StatementBlock
import org.xvm.compiler.ast.TypeCompositionStatement
import org.xvm.lsp.adapter.Position
import org.xvm.lsp.adapter.Range

/**
 * Reading a compiled document's AST for the questions an editor asks about a position.
 *
 * The AST exposes children and source spans for folding and selection. Semantic queries use
 * the compiler-owned snapshot cached by [XdkAdapter].
 */
internal object XdkAst {
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
            val next = node.childList().firstOrNull { it.contains(line, column) } ?: return chain
            node = next
        }
    }

    /**
     * The regions worth collapsing: anything that spans more than one line and is a block or a
     * declaration. An editor offers a fold per region, so a region per expression would be noise.
     */
    fun foldingRegions(root: AstNode?): List<Pair<Int, Int>> {
        val found = LinkedHashSet<Pair<Int, Int>>()

        fun walk(node: AstNode) {
            if (node is StatementBlock || node is TypeCompositionStatement) {
                val start = lineOf(node.startPosition)
                val end = lineOf(node.endPosition)
                if (end > start) {
                    found += start to end
                }
            }
            node.childList().forEach(::walk)
        }
        root?.let(::walk)
        return found.toList()
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

    /**
     * [AstNode.children] is an iterator that supports replacement during a compiler pass; a
     * reader wants a list, taken once.
     */
    private fun AstNode.childList(): List<AstNode> {
        val kids = mutableListOf<AstNode>()
        children().forEachRemaining { kids += it }
        return kids
    }

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
