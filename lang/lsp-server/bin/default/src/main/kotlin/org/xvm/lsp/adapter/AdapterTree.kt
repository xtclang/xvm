package org.xvm.lsp.adapter

/**
 * Backend-agnostic interface for a parsed syntax tree.
 *
 * Implementations provide access to the root node, source text, and positional
 * node lookup. Both tree-sitter and compiler-based parsers can implement this
 * interface, allowing formatting and code action logic to work with either backend.
 *
 * @see AdapterNode for the node interface
 */
interface AdapterTree {
    /** The root node of the syntax tree. */
    val root: AdapterNode

    /** The original source text that was parsed. */
    val source: String

    /** Whether this tree contains any syntax errors. */
    val hasErrors: Boolean

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
    ): AdapterNode?
}

/**
 * Backend-agnostic interface for a syntax tree node.
 *
 * Provides the subset of node operations needed by shared formatting and
 * code action logic. Implementations wrap backend-specific node types
 * (tree-sitter nodes, compiler AST nodes, etc.).
 *
 * @see AdapterTree for the tree interface
 */
interface AdapterNode {
    /** The type of this node (e.g., "class_declaration", "identifier"). */
    val type: String

    /** The parent node, or null if this is the root. */
    val parent: AdapterNode?

    /** All children of this node. */
    val children: List<AdapterNode>

    /** The 0-based start line. */
    val startLine: Int

    /** The 0-based end line. */
    val endLine: Int

    /** The 0-based start column. */
    val startColumn: Int

    /** The 0-based end column. */
    val endColumn: Int

    /** The text content of this node from the source. */
    val text: String

    /** Whether this node represents a syntax error. */
    val isError: Boolean

    /** Whether this node is missing (inserted by error recovery). */
    val isMissing: Boolean

    /**
     * Get a child node by field name.
     *
     * Field-based lookup is preferred when available — it is O(1) and
     * position-independent, unlike [childByType] which scans linearly.
     */
    fun childByFieldName(fieldName: String): AdapterNode?

    /**
     * Get the first child node with the given type.
     *
     * Fallback for nodes without field definitions. Prefer [childByFieldName].
     */
    fun childByType(nodeType: String): AdapterNode?
}
