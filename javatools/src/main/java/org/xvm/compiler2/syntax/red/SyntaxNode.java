package org.xvm.compiler2.syntax.red;

import java.util.Objects;

import org.xvm.compiler2.syntax.SyntaxKind;
import org.xvm.compiler2.syntax.green.GreenExpression;
import org.xvm.compiler2.syntax.green.GreenNode;
import org.xvm.compiler2.syntax.green.GreenStatement;
import org.xvm.compiler2.syntax.green.GreenToken;
import org.xvm.compiler2.syntax.green.GreenType;

/**
 * A red tree node - a navigation wrapper around an immutable green node.
 * <p>
 * Red nodes provide:
 * <ul>
 *   <li>Parent navigation (green nodes have no parent pointer)</li>
 *   <li>Absolute position in the source</li>
 *   <li>Convenient typed child access</li>
 * </ul>
 * <p>
 * Red nodes are cheap to create and disposable. They should not be cached long-term.
 * When the source is edited, old red nodes become stale and new ones are created on demand.
 * <p>
 * The green tree structure is preserved - the red tree is just a facade over it.
 */
public class SyntaxNode {

    /**
     * The underlying green node (immutable).
     */
    private final GreenNode green;

    /**
     * The parent red node, or null for root.
     */
    private final SyntaxNode parent;

    /**
     * The absolute character position in the source.
     */
    private final int position;

    /**
     * Cached children (lazily populated).
     */
    private SyntaxNode[] children;

    /**
     * Construct a red node.
     *
     * @param green    the underlying green node
     * @param parent   the parent red node (null for root)
     * @param position the absolute position in source
     */
    protected SyntaxNode(GreenNode green, SyntaxNode parent, int position) {
        this.green = Objects.requireNonNull(green, "green");
        this.parent = parent;
        this.position = position;
    }

    /**
     * Create a root red node from a green tree.
     *
     * @param green the root green node
     * @return the root red node
     */
    public static SyntaxNode createRoot(GreenNode green) {
        return new SyntaxNode(green, null, 0);
    }

    // -------------------------------------------------------------------------
    // Green node access
    // -------------------------------------------------------------------------

    /**
     * @return the underlying immutable green node
     */
    public GreenNode getGreen() {
        return green;
    }

    /**
     * @return the syntax kind
     */
    public SyntaxKind getKind() {
        return green.getKind();
    }

    /**
     * @return true if this is a token (terminal)
     */
    public boolean isToken() {
        return green.isToken();
    }

    // -------------------------------------------------------------------------
    // Navigation
    // -------------------------------------------------------------------------

    /**
     * @return the parent node, or null if this is the root
     */
    public SyntaxNode getParent() {
        return parent;
    }

    /**
     * @return true if this is the root node
     */
    public boolean isRoot() {
        return parent == null;
    }

    /**
     * @return the root of this tree
     */
    public SyntaxNode getRoot() {
        SyntaxNode node = this;
        while (node.parent != null) {
            node = node.parent;
        }
        return node;
    }

    /**
     * @return the number of children
     */
    public int getChildCount() {
        return green.getChildCount();
    }

    /**
     * Get a child node by index.
     * <p>
     * Child red nodes are created lazily and cached.
     *
     * @param index the child index
     * @return the child red node
     * @throws IndexOutOfBoundsException if index is out of range
     */
    public SyntaxNode getChild(int index) {
        int count = green.getChildCount();
        if (index < 0 || index >= count) {
            throw new IndexOutOfBoundsException(index);
        }

        // Lazy initialization of children array
        if (children == null) {
            children = new SyntaxNode[count];
        }

        // Lazy creation of individual child
        if (children[index] == null) {
            GreenNode greenChild = green.getChild(index);
            if (greenChild != null) {
                int childPos = computeChildPosition(index);
                children[index] = new SyntaxNode(greenChild, this, childPos);
            }
        }

        return children[index];
    }

    /**
     * Compute the position of a child.
     */
    private int computeChildPosition(int index) {
        int pos = position;
        for (int i = 0; i < index; i++) {
            GreenNode child = green.getChild(i);
            if (child != null) {
                pos += child.getFullWidth();
            }
        }
        return pos;
    }

    // -------------------------------------------------------------------------
    // Position and span
    // -------------------------------------------------------------------------

    /**
     * @return the absolute position in source (start of this node including trivia)
     */
    public int getPosition() {
        return position;
    }

    /**
     * @return the full width of this node including trivia
     */
    public int getFullWidth() {
        return green.getFullWidth();
    }

    /**
     * @return the end position (exclusive)
     */
    public int getEndPosition() {
        return position + green.getFullWidth();
    }

    /**
     * Check if this node spans a position.
     *
     * @param pos the position to check
     * @return true if pos is within this node's span
     */
    public boolean containsPosition(int pos) {
        return pos >= position && pos < getEndPosition();
    }

    // -------------------------------------------------------------------------
    // Find nodes
    // -------------------------------------------------------------------------

    /**
     * Find the deepest node that contains the given position.
     *
     * @param pos the position to find
     * @return the deepest containing node, or null if position is outside
     */
    public SyntaxNode findNode(int pos) {
        if (!containsPosition(pos)) {
            return null;
        }

        // Check children
        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            SyntaxNode child = getChild(i);
            if (child != null && child.containsPosition(pos)) {
                return child.findNode(pos);
            }
        }

        // No child contains it, this is the deepest
        return this;
    }

    /**
     * Find the deepest token at the given position.
     *
     * @param pos the position to find
     * @return the token at that position, or null
     */
    public SyntaxNode findToken(int pos) {
        SyntaxNode node = findNode(pos);
        while (node != null && !node.isToken()) {
            // Go to first child or null
            if (node.getChildCount() > 0) {
                node = node.getChild(0);
            } else {
                break;
            }
        }
        return node != null && node.isToken() ? node : null;
    }

    // -------------------------------------------------------------------------
    // Typed access
    // -------------------------------------------------------------------------

    /**
     * Get this node as a token, or null if not a token.
     */
    public GreenToken asToken() {
        return green instanceof GreenToken t ? t : null;
    }

    /**
     * @return true if this is an expression
     */
    public boolean isExpression() {
        return green instanceof GreenExpression;
    }

    /**
     * @return true if this is a statement
     */
    public boolean isStatement() {
        return green instanceof GreenStatement;
    }

    /**
     * @return true if this is a type
     */
    public boolean isType() {
        return green instanceof GreenType;
    }

    // -------------------------------------------------------------------------
    // Tree modification (produces new tree)
    // -------------------------------------------------------------------------

    /**
     * Create a new tree with this node replaced.
     * <p>
     * This walks up to the root, creating new red nodes along the path.
     *
     * @param newGreen the replacement green node
     * @return the new root red node
     */
    public SyntaxNode replaceWith(GreenNode newGreen) {
        if (parent == null) {
            // This is root, just return new root
            return createRoot(newGreen);
        }

        // Find our index in parent
        int index = -1;
        for (int i = 0; i < parent.getChildCount(); i++) {
            if (parent.getChild(i) == this) {
                index = i;
                break;
            }
        }

        if (index == -1) {
            throw new IllegalStateException("Node not found in parent");
        }

        // Create new parent green with replaced child
        GreenNode newParentGreen = parent.green.withChild(index, newGreen);

        // Recursively replace up the tree
        return parent.replaceWith(newParentGreen);
    }

    // -------------------------------------------------------------------------
    // Object methods
    // -------------------------------------------------------------------------

    @Override
    public String toString() {
        return "SyntaxNode[" + getKind() + " @ " + position + ", width=" + getFullWidth() + "]";
    }

    /**
     * Pretty-print this node and its descendants.
     *
     * @return a multi-line string representation
     */
    public String toTreeString() {
        StringBuilder sb = new StringBuilder();
        appendTree(sb, 0);
        return sb.toString();
    }

    private void appendTree(StringBuilder sb, int indent) {
        sb.append("  ".repeat(indent));
        if (isToken()) {
            GreenToken token = asToken();
            sb.append(getKind()).append(" \"").append(token.getText()).append("\"");
        } else {
            sb.append(getKind());
        }
        sb.append(" [").append(position).append("..").append(getEndPosition()).append(")\n");

        for (int i = 0; i < getChildCount(); i++) {
            SyntaxNode child = getChild(i);
            if (child != null) {
                child.appendTree(sb, indent + 1);
            }
        }
    }
}
