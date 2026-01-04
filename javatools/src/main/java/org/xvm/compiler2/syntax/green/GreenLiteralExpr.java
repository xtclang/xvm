package org.xvm.compiler2.syntax.green;

import org.xvm.compiler2.syntax.SyntaxKind;

/**
 * A literal expression (numbers, strings, booleans, null).
 */
public final class GreenLiteralExpr extends GreenExpression {

    /**
     * The literal token.
     */
    private final GreenToken literal;

    /**
     * Private constructor - use factory methods.
     */
    private GreenLiteralExpr(GreenToken literal) {
        super(SyntaxKind.LITERAL_EXPRESSION, literal.getFullWidth());
        this.literal = literal;
    }

    /**
     * Create a literal expression.
     *
     * @param literal the literal token
     * @return the interned expression
     */
    public static GreenLiteralExpr create(GreenToken literal) {
        return intern(new GreenLiteralExpr(literal));
    }

    /**
     * Create an integer literal expression.
     *
     * @param value the integer value
     * @return the interned expression
     */
    public static GreenLiteralExpr intLiteral(long value) {
        return create(GreenToken.intLiteral(value));
    }

    /**
     * Create a floating-point literal expression.
     *
     * @param value the double value
     * @return the interned expression
     */
    public static GreenLiteralExpr fpLiteral(double value) {
        return create(GreenToken.fpLiteral(value));
    }

    /**
     * @return the literal token
     */
    public GreenToken getLiteral() {
        return literal;
    }

    /**
     * @return the literal value (Long, Double, String, etc.)
     */
    public Object getValue() {
        return literal.getValue();
    }

    @Override
    public int getChildCount() {
        return 1;
    }

    @Override
    public GreenNode getChild(int index) {
        if (index == 0) {
            return literal;
        }
        throw new IndexOutOfBoundsException(index);
    }

    @Override
    public GreenNode withChild(int index, GreenNode child) {
        if (index == 0) {
            return child == literal ? this : create((GreenToken) child);
        }
        throw new IndexOutOfBoundsException(index);
    }

    @Override
    public String toString() {
        return "LiteralExpr[" + literal.getText() + "]";
    }
}
