package org.xvm.lsp.treesitter

import io.github.treesitter.jtreesitter.Point
import io.github.treesitter.jtreesitter.Tree
import java.io.Closeable

/**
 * Wrapper around a Tree-sitter parsed tree for XTC sources.
 *
 * The tree provides access to the syntax structure of the source code.
 * It can be used for incremental reparsing when the source changes.
 */
class XtcTree internal constructor(
    internal val tsTree: Tree,
    val source: String,
) : Closeable {
    @Volatile
    private var closed = false

    /**
     * Get the root node of the syntax tree.
     */
    val root: XtcNode
        get() {
            checkNotClosed()
            return XtcNode(tsTree.rootNode, source)
        }

    /**
     * Check if this tree has any syntax errors.
     */
    val hasErrors: Boolean
        get() {
            checkNotClosed()
            return root.hasError
        }

    /**
     * Find the smallest node at the given position.
     *
     * @param line   0-based line number
     * @param column 0-based column number
     * @return the node at that position, or null if none
     */
    fun nodeAt(
        line: Int,
        column: Int,
    ): XtcNode? {
        checkNotClosed()
        val point = Point(line, column)
        return tsTree.rootNode
            .getDescendant(point, point)
            .orElse(null)
            ?.let { XtcNode(it, source) }
    }

    /**
     * Find the deepest named node at the given position.
     *
     * Named nodes are nodes that appear in the grammar (e.g., "class_declaration"),
     * as opposed to anonymous nodes (e.g., literal punctuation like "{").
     *
     * @param line   0-based line number
     * @param column 0-based column number
     * @return the named node at that position, or null if none
     */
    @Suppress("unused")
    fun namedNodeAt(
        line: Int,
        column: Int,
    ): XtcNode? {
        var node = nodeAt(line, column)
        while (node != null && !node.isNamed) {
            node = node.parent
        }
        return node
    }

    override fun close() {
        if (!closed) {
            closed = true
            tsTree.close()
        }
    }

    private fun checkNotClosed() {
        check(!closed) { "Tree has been closed" }
    }
}
