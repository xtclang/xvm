package org.xvm.compiler2.syntax.green;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.xvm.compiler2.syntax.SyntaxKind;

/**
 * Base class for all immutable green (syntax) nodes in the Roslyn-style tree.
 * <p>
 * Green nodes are:
 * <ul>
 *   <li>Completely immutable (all fields are final)</li>
 *   <li>Have no parent pointer (enables structural sharing)</li>
 *   <li>Interned for deduplication (same content = same object)</li>
 *   <li>Support copy-on-write via withChild()</li>
 * </ul>
 * <p>
 * This is a sealed class hierarchy - all subclasses must be in this package.
 */
public abstract sealed class GreenNode
        permits GreenToken, GreenExpression, GreenStatement, GreenDeclaration, GreenType, GreenList {

    /**
     * The syntax kind of this node.
     */
    private final SyntaxKind kind;

    /**
     * The full width of this node in characters, including any leading/trailing trivia.
     */
    private final int fullWidth;

    /**
     * Cached hash code for efficient interning. Computed lazily.
     */
    private int hash;

    /**
     * Whether the hash has been computed.
     */
    private boolean hashComputed;

    /**
     * Construct a green node.
     *
     * @param kind      the syntax kind
     * @param fullWidth the total character width including trivia
     */
    protected GreenNode(SyntaxKind kind, int fullWidth) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.fullWidth = fullWidth;
        // Hash is computed lazily to avoid calling virtual methods in constructor
    }

    /**
     * @return the syntax kind of this node
     */
    public SyntaxKind getKind() {
        return kind;
    }

    /**
     * @return the full width of this node in characters
     */
    public int getFullWidth() {
        return fullWidth;
    }

    /**
     * @return the number of children this node has
     */
    public abstract int getChildCount();

    /**
     * Get a child node by index.
     *
     * @param index the child index (0-based)
     * @return the child node
     * @throws IndexOutOfBoundsException if index is out of range
     */
    public abstract GreenNode getChild(int index);

    /**
     * Create a copy of this node with a different child at the specified index.
     * <p>
     * If the new child is the same as the current child (by identity),
     * returns this node unchanged (structural sharing).
     *
     * @param index the child index to replace
     * @param child the new child node
     * @return a new node with the replaced child, or this if unchanged
     * @throws IndexOutOfBoundsException if index is out of range
     */
    public abstract GreenNode withChild(int index, GreenNode child);

    /**
     * @return true if this is a token (terminal node)
     */
    public boolean isToken() {
        return false;
    }

    /**
     * Accept a visitor.
     *
     * @param visitor the visitor
     * @param <R>     the result type
     * @return the visitor's result
     */
    public abstract <R> R accept(GreenVisitor<R> visitor);

    /**
     * Compute the hash code based on kind and children.
     */
    protected int computeHash() {
        int h = kind.hashCode();
        int childCount = getChildCount();
        for (int i = 0; i < childCount; i++) {
            GreenNode child = getChild(i);
            if (child != null) {
                h = 31 * h + System.identityHashCode(child);
            }
        }
        return h;
    }

    @Override
    public final int hashCode() {
        if (!hashComputed) {
            hash = computeHash();
            hashComputed = true;
        }
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        GreenNode other = (GreenNode) obj;
        if (kind != other.kind || fullWidth != other.fullWidth) {
            return false;
        }

        int childCount = getChildCount();
        if (childCount != other.getChildCount()) {
            return false;
        }

        for (int i = 0; i < childCount; i++) {
            if (getChild(i) != other.getChild(i)) {
                return false;
            }
        }
        return true;
    }

    // -------------------------------------------------------------------------
    // Interning support
    // -------------------------------------------------------------------------

    /**
     * Cache for interned nodes. Uses weak references to allow GC when nodes
     * are no longer reachable.
     */
    private static final ConcurrentHashMap<GreenNode, WeakReference<GreenNode>> INTERN_CACHE =
            new ConcurrentHashMap<>();

    /**
     * Intern a node, returning the canonical instance if one exists.
     * <p>
     * This enables structural sharing: if two nodes have identical content,
     * they become the same object.
     *
     * @param node the node to intern
     * @param <T>  the node type
     * @return the canonical instance
     */
    @SuppressWarnings("unchecked")
    protected static <T extends GreenNode> T intern(T node) {
        // Fast path: check if already interned
        WeakReference<GreenNode> ref = INTERN_CACHE.get(node);
        if (ref != null) {
            GreenNode cached = ref.get();
            if (cached != null) {
                return (T) cached;
            }
        }

        // Not found or expired - add this node
        INTERN_CACHE.put(node, new WeakReference<>(node));
        return node;
    }

    /**
     * Clear the intern cache. Primarily for testing.
     */
    public static void clearInternCache() {
        INTERN_CACHE.clear();
    }

    /**
     * @return the current size of the intern cache
     */
    public static int getInternCacheSize() {
        return INTERN_CACHE.size();
    }
}
