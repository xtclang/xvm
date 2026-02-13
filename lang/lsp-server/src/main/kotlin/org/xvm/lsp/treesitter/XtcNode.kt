package org.xvm.lsp.treesitter

import io.github.treesitter.jtreesitter.Node
import java.util.Optional

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
        get() = tsNode.parent.wrap()

    /**
     * Get a child node by index.
     */
    @Suppress("unused") // TODO: Will be used for manual tree traversal in formatting and folding
    fun child(index: Int): XtcNode? = tsNode.getChild(index).wrap()

    /**
     * Get a named child node by index.
     */
    @Suppress("unused") // TODO: Will be used for manual tree traversal in formatting and folding
    fun namedChild(index: Int): XtcNode? = tsNode.getNamedChild(index).wrap()

    /**
     * Get a child node by field name (O(1) lookup via tree-sitter's internal field table).
     *
     * The XTC grammar defines field names on all major constructs via `field()` in grammar.js,
     * enabling direct semantic access to named children. This is the **preferred** API for
     * navigating the AST because:
     *
     * - **O(1) performance**: tree-sitter resolves fields via a compile-time field table,
     *   unlike [childByType] which scans all children linearly (O(n)).
     * - **Position-independent**: fields identify children by semantic role, not by their
     *   position among siblings. Adding optional children (e.g., annotations, modifiers)
     *   before a node won't break field-based lookups.
     * - **Self-documenting**: `node.childByFieldName("name")` is clearer than
     *   `node.childByType("identifier")` which could match any identifier child.
     *
     * ## Available Fields by Node Type
     *
     * **Declarations**: `name`, `type_params`, `body`, `return_type`, `parameters`, `type`, `value`
     * **Expressions**: `function`, `arguments`, `object`, `member`, `left`, `right`, `size`
     * **Statements**: `condition`, `consequence`, `alternative`, `body`, `iterable`, `label`
     * **Other**: `path`/`alias` (imports), `constraint` (type_parameter), `default` (parameter)
     *
     * ## What Can Be Built on Top of Fields
     *
     * Fields unlock higher-level features that were previously fragile or impossible:
     * - **Tree-sitter queries** can use `name:` field syntax for robust pattern matching
     * - **Rename refactoring** can reliably find the `name` field of any declaration
     * - **Signature help** can extract `parameters` and `return_type` without positional guessing
     * - **Semantic tokens** can classify `member` vs `object` in member expressions
     * - **Code navigation** can distinguish a method's `body` from its `parameters`
     *
     * @see childByType for fallback when a grammar node type lacks field definitions
     */
    fun childByFieldName(fieldName: String): XtcNode? = tsNode.getChildByFieldName(fieldName).wrap()

    /**
     * Get the first child node with the given type (O(n) linear scan).
     *
     * This is a **fallback** for nodes that don't have field definitions in the grammar,
     * or for querying anonymous/keyword children (e.g., `"construct"`, `"static"`).
     *
     * Prefer [childByFieldName] when a field is available — it is faster (O(1)),
     * position-independent, and self-documenting.
     */
    fun childByType(nodeType: String): XtcNode? = children.find { it.type == nodeType }

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
        get() = tsNode.nextSibling.wrap()

    /**
     * Get the previous sibling node.
     */
    @Suppress("unused") // TODO: Will be used for folding ranges and statement grouping
    val prevSibling: XtcNode?
        get() = tsNode.prevSibling.wrap()

    /**
     * Get the next named sibling node.
     */
    @Suppress("unused") // TODO: Will be used for sibling-aware code actions
    val nextNamedSibling: XtcNode?
        get() = tsNode.nextNamedSibling.wrap()

    /**
     * Get the previous named sibling node.
     */
    @Suppress("unused") // TODO: Will be used for sibling-aware code actions
    val prevNamedSibling: XtcNode?
        get() = tsNode.prevNamedSibling.wrap()

    /**
     * Get the underlying tree-sitter node for advanced operations.
     */
    @Suppress("unused") // TODO: Will be used for advanced tree-sitter operations not covered by this wrapper
    internal fun getTsNode(): Node = tsNode

    /** Convert a Java Optional<Node> to an XtcNode?, avoiding Java's Optional.map() in favor of Kotlin's ?. */
    private fun Optional<Node>.wrap(): XtcNode? = orElse(null)?.let { XtcNode(it, source) }

    override fun toString(): String = "XtcNode(type=$type, range=[$startLine:$startColumn-$endLine:$endColumn])"
}
