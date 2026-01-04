package org.xvm.compiler2.syntax.green;

import java.util.Arrays;

import org.xvm.compiler2.syntax.SyntaxKind;

/**
 * A node that holds a list of child nodes (e.g., argument lists, statement lists).
 * <p>
 * This is used for syntax constructs that have a variable number of children.
 */
public final class GreenList extends GreenNode {

    /**
     * The child nodes.
     */
    private final GreenNode[] children;

    /**
     * Private constructor - use factory methods.
     */
    private GreenList(SyntaxKind kind, GreenNode[] children) {
        super(kind, computeWidth(children));
        this.children = children;
    }

    private static int computeWidth(GreenNode[] children) {
        int width = 0;
        for (GreenNode child : children) {
            if (child != null) {
                width += child.getFullWidth();
            }
        }
        return width;
    }

    /**
     * Create a list node.
     *
     * @param kind     the syntax kind
     * @param children the child nodes
     * @return the interned list
     */
    public static GreenList create(SyntaxKind kind, GreenNode... children) {
        return intern(new GreenList(kind, children.clone()));
    }

    /**
     * Create an empty list.
     *
     * @param kind the syntax kind
     * @return the interned empty list
     */
    public static GreenList empty(SyntaxKind kind) {
        return intern(new GreenList(kind, new GreenNode[0]));
    }

    @Override
    public int getChildCount() {
        return children.length;
    }

    @Override
    public GreenNode getChild(int index) {
        return children[index];
    }

    @Override
    public GreenNode withChild(int index, GreenNode child) {
        if (children[index] == child) {
            return this;
        }
        GreenNode[] newChildren = children.clone();
        newChildren[index] = child;
        return intern(new GreenList(getKind(), newChildren));
    }

    /**
     * Create a new list with an additional child appended.
     *
     * @param child the child to append
     * @return a new list with the child added
     */
    public GreenList withAppended(GreenNode child) {
        GreenNode[] newChildren = Arrays.copyOf(children, children.length + 1);
        newChildren[children.length] = child;
        return intern(new GreenList(getKind(), newChildren));
    }

    @Override
    public <R> R accept(GreenVisitor<R> visitor) {
        return visitor.visitList(this);
    }

    @Override
    public String toString() {
        return "List[" + getKind() + ", " + children.length + " children]";
    }
}
