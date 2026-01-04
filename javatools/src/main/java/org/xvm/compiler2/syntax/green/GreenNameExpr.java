package org.xvm.compiler2.syntax.green;

import org.xvm.compiler2.syntax.SyntaxKind;

/**
 * A name expression (simple identifier reference).
 */
public final class GreenNameExpr extends GreenExpression {

    /**
     * The identifier token.
     */
    private final GreenToken identifier;

    /**
     * Private constructor - use factory methods.
     */
    private GreenNameExpr(GreenToken identifier) {
        super(SyntaxKind.NAME_EXPRESSION, identifier.getFullWidth());
        this.identifier = identifier;
    }

    /**
     * Create a name expression.
     *
     * @param identifier the identifier token
     * @return the interned expression
     */
    public static GreenNameExpr create(GreenToken identifier) {
        return intern(new GreenNameExpr(identifier));
    }

    /**
     * Create a name expression from a string.
     *
     * @param name the identifier name
     * @return the interned expression
     */
    public static GreenNameExpr create(String name) {
        return create(GreenToken.identifier(name));
    }

    /**
     * @return the identifier token
     */
    public GreenToken getIdentifier() {
        return identifier;
    }

    /**
     * @return the identifier name
     */
    public String getName() {
        return identifier.getText();
    }

    @Override
    public int getChildCount() {
        return 1;
    }

    @Override
    public GreenNode getChild(int index) {
        if (index == 0) {
            return identifier;
        }
        throw new IndexOutOfBoundsException(index);
    }

    @Override
    public GreenNode withChild(int index, GreenNode child) {
        if (index == 0) {
            return child == identifier ? this : create((GreenToken) child);
        }
        throw new IndexOutOfBoundsException(index);
    }

    @Override
    public String toString() {
        return "NameExpr[" + identifier.getText() + "]";
    }
}
