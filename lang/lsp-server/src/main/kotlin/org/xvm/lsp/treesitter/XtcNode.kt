package org.xvm.lsp.treesitter

import io.github.treesitter.jtreesitter.Node

/**
 * Wrapper around a Tree-sitter syntax node for XTC sources.
 *
 * Provides a Kotlin-friendly API for traversing and querying the syntax tree.
 *
 * ## API Completeness Note
 *
 * This class exposes the complete tree-sitter Node API, even though not all methods are
 * currently used. The unused methods are intentionally included for future LSP features:
 *
 * | Method | Future Use |
 * |--------|------------|
 * | `child(index)`, `namedChild(index)` | Manual tree traversal for formatting, folding |
 * | `childCount`, `namedChildCount` | Iteration bounds for tree walking |
 * | `namedChildren` | Filtered iteration for semantic analysis |
 * | `nextSibling`, `prevSibling` | Folding ranges, statement grouping |
 * | `nextNamedSibling`, `prevNamedSibling` | Sibling-aware code actions |
 * | `startByte`, `endByte` | Byte-level edits, incremental parsing |
 * | `isNamed` | Filtering named vs anonymous (punctuation) nodes |
 *
 * @see XtcTree
 * @see XtcQueryEngine
 */
class XtcNode internal constructor(
    private val tsNode: Node,
    private val source: String,
) {
    /**
     * The type of this node (e.g., "class_declaration", "identifier").
     */
    val type: String
        get() = tsNode.type

    /**
     * The text content of this node from the source.
     */
    val text: String
        get() = source.substring(tsNode.startByte, tsNode.endByte)

    /**
     * Whether this is a named node (appears in grammar rules) vs anonymous (literal tokens).
     */
    @Suppress("unused") // TODO: Will be used for semantic tokens - filtering out punctuation nodes
    val isNamed: Boolean
        get() = tsNode.isNamed

    /**
     * Whether this node represents a syntax error.
     */
    val isError: Boolean
        get() = tsNode.isError

    /**
     * Whether this node is missing (inserted by error recovery).
     */
    val isMissing: Boolean
        get() = tsNode.isMissing

    /**
     * Whether this node or any of its descendants has an error.
     */
    val hasError: Boolean
        get() = tsNode.hasError()

    /**
     * The 0-based start line of this node.
     */
    val startLine: Int
        get() = tsNode.startPoint.row()

    /**
     * The 0-based start column of this node.
     */
    val startColumn: Int
        get() = tsNode.startPoint.column()

    /**
     * The 0-based end line of this node.
     */
    val endLine: Int
        get() = tsNode.endPoint.row()

    /**
     * The 0-based end column of this node.
     */
    val endColumn: Int
        get() = tsNode.endPoint.column()

    /**
     * The byte offset where this node starts.
     */
    @Suppress("unused") // TODO: Will be used for incremental parsing and byte-level text edits
    val startByte: Int
        get() = tsNode.startByte

    /**
     * The byte offset where this node ends.
     */
    @Suppress("unused") // TODO: Will be used for incremental parsing and byte-level text edits
    val endByte: Int
        get() = tsNode.endByte

    /**
     * The number of children this node has.
     */
    @Suppress("unused") // TODO: Will be used for manual tree traversal in formatting and folding
    val childCount: Int
        get() = tsNode.childCount

    /**
     * The number of named children this node has.
     */
    @Suppress("unused") // TODO: Will be used for manual tree traversal in formatting and folding
    val namedChildCount: Int
        get() = tsNode.namedChildCount

    /**
     * Get the parent node, or null if this is the root.
     */
    val parent: XtcNode?
        get() = tsNode.parent.map { XtcNode(it, source) }.orElse(null)

    /**
     * Get a child node by index.
     */
    @Suppress("unused") // TODO: Will be used for manual tree traversal in formatting and folding
    fun child(index: Int): XtcNode? = tsNode.getChild(index).map { XtcNode(it, source) }.orElse(null)

    /**
     * Get a named child node by index.
     */
    @Suppress("unused") // TODO: Will be used for manual tree traversal in formatting and folding
    fun namedChild(index: Int): XtcNode? = tsNode.getNamedChild(index).map { XtcNode(it, source) }.orElse(null)

    /**
     * Get a child node by field name.
     */
    fun childByFieldName(fieldName: String): XtcNode? = tsNode.getChildByFieldName(fieldName).map { XtcNode(it, source) }.orElse(null)

    /**
     * Get all children of this node.
     */
    val children: List<XtcNode>
        get() = (0 until tsNode.childCount).mapNotNull { child(it) }

    /**
     * Get all named children of this node.
     */
    @Suppress("unused") // TODO: Will be used for semantic analysis and document symbols
    val namedChildren: List<XtcNode>
        get() = (0 until tsNode.namedChildCount).mapNotNull { namedChild(it) }

    /**
     * Get the next sibling node.
     */
    @Suppress("unused") // TODO: Will be used for folding ranges and statement grouping
    val nextSibling: XtcNode?
        get() = tsNode.nextSibling.map { XtcNode(it, source) }.orElse(null)

    /**
     * Get the previous sibling node.
     */
    @Suppress("unused") // TODO: Will be used for folding ranges and statement grouping
    val prevSibling: XtcNode?
        get() = tsNode.prevSibling.map { XtcNode(it, source) }.orElse(null)

    /**
     * Get the next named sibling node.
     */
    @Suppress("unused") // TODO: Will be used for sibling-aware code actions
    val nextNamedSibling: XtcNode?
        get() = tsNode.nextNamedSibling.map { XtcNode(it, source) }.orElse(null)

    /**
     * Get the previous named sibling node.
     */
    @Suppress("unused") // TODO: Will be used for sibling-aware code actions
    val prevNamedSibling: XtcNode?
        get() = tsNode.prevNamedSibling.map { XtcNode(it, source) }.orElse(null)

    /**
     * Get the underlying tree-sitter node for advanced operations.
     */
    @Suppress("unused") // TODO: Will be used for advanced tree-sitter operations not covered by this wrapper
    internal fun getTsNode(): Node = tsNode

    override fun toString(): String = "XtcNode(type=$type, range=[$startLine:$startColumn-$endLine:$endColumn])"
}
