package org.xvm.lsp.adapter.xdk

import org.xvm.compiler.Source
import org.xvm.compiler.ast.AstNode
import org.xvm.compiler.ast.Expression
import org.xvm.compiler.ast.NameExpression
import org.xvm.compiler.ast.StatementBlock
import org.xvm.compiler.ast.TypeCompositionStatement
import org.xvm.lsp.adapter.Position
import org.xvm.lsp.adapter.Range

/**
 * Reading a compiled document's AST for the questions an editor asks about a position.
 *
 * Everything here works on what [AstNode] exposes to a caller outside the compiler: a parent, its
 * children, and where it starts and ends. That is enough to say what encloses a position and how
 * far a thing extends, which covers folding, selection and - because [NameExpression] does expose
 * the name that was written - finding the other places the same name appears.
 *
 * It is not enough to say what a name *means*. That is the next wall, and it is a real one: see
 * [XdkResolution].
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
     * The name the cursor is on, if it is on one.
     */
    fun nameAt(
        root: AstNode?,
        line: Int,
        column: Int,
    ): NameExpression? = chainAt(root, line, column).filterIsInstance<NameExpression>().lastOrNull()

    /**
     * Every node under this one, itself included, in source order.
     *
     * The same node can be reached by more than one path - a constructor parameter belongs to the
     * declaration and to the property it becomes - so a caller that turns these into places in the
     * text has to say what it means by the same place. [namesIn] does; this does not.
     */
    fun nodesIn(root: AstNode?): List<AstNode> {
        val found = mutableListOf<AstNode>()

        fun walk(node: AstNode) {
            found += node
            node.childList().forEach(::walk)
        }
        root?.let(::walk)
        return found.sortedBy { it.startPosition }
    }

    /** Every name written in the document, in source order, one per place in the text. */
    fun namesIn(root: AstNode?): List<NameExpression> =
        nodesIn(root)
            .filterIsInstance<NameExpression>()
            .distinctBy { it.startPosition to it.endPosition }

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

    /**
     * The type of the expression at a position, when the compiler got far enough to know it.
     *
     * An expression only has a type once it has been validated, and asking one that has not
     * throws - which is the normal case in a document that does not compile, and an editor asks
     * about those constantly.
     */
    fun typeAt(
        root: AstNode?,
        line: Int,
        column: Int,
    ): String? =
        chainAt(root, line, column)
            .filterIsInstance<Expression>()
            .lastOrNull { it.isValidated && it.type != null }
            ?.type
            ?.valueString

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
