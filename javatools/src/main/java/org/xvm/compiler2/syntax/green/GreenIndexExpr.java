package org.xvm.compiler2.syntax.green;

import org.xvm.compiler2.syntax.SyntaxKind;

/**
 * An index/subscript expression: target[index]
 */
public final class GreenIndexExpr extends GreenExpression {

    private final GreenExpression target;
    private final GreenToken openBracket;
    private final GreenExpression index;
    private final GreenToken closeBracket;

    private GreenIndexExpr(GreenExpression target, GreenToken openBracket,
                           GreenExpression index, GreenToken closeBracket) {
        super(SyntaxKind.ARRAY_ACCESS_EXPRESSION,
                target.getFullWidth() + openBracket.getFullWidth() +
                index.getFullWidth() + closeBracket.getFullWidth());
        this.target = target;
        this.openBracket = openBracket;
        this.index = index;
        this.closeBracket = closeBracket;
    }

    /**
     * Create an index expression.
     *
     * @param target       the target expression
     * @param openBracket  the '[' token
     * @param index        the index expression
     * @param closeBracket the ']' token
     * @return the interned expression
     */
    public static GreenIndexExpr create(GreenExpression target, GreenToken openBracket,
                                        GreenExpression index, GreenToken closeBracket) {
        return intern(new GreenIndexExpr(target, openBracket, index, closeBracket));
    }

    /**
     * Create an index expression with default brackets.
     *
     * @param target the target expression
     * @param index  the index expression
     * @return the interned expression
     */
    public static GreenIndexExpr create(GreenExpression target, GreenExpression index) {
        return create(target,
                GreenToken.create(SyntaxKind.LBRACKET, "["),
                index,
                GreenToken.create(SyntaxKind.RBRACKET, "]"));
    }

    /**
     * @return the target expression
     */
    public GreenExpression getTarget() {
        return target;
    }

    /**
     * @return the opening bracket
     */
    public GreenToken getOpenBracket() {
        return openBracket;
    }

    /**
     * @return the index expression
     */
    public GreenExpression getIndex() {
        return index;
    }

    /**
     * @return the closing bracket
     */
    public GreenToken getCloseBracket() {
        return closeBracket;
    }

    @Override
    public int getChildCount() {
        return 4;
    }

    @Override
    public GreenNode getChild(int index) {
        return switch (index) {
            case 0 -> target;
            case 1 -> openBracket;
            case 2 -> this.index;
            case 3 -> closeBracket;
            default -> throw new IndexOutOfBoundsException(index);
        };
    }

    @Override
    public GreenNode withChild(int index, GreenNode child) {
        return switch (index) {
            case 0 -> child == target ? this : create((GreenExpression) child, openBracket, this.index, closeBracket);
            case 1 -> child == openBracket ? this : create(target, (GreenToken) child, this.index, closeBracket);
            case 2 -> child == this.index ? this : create(target, openBracket, (GreenExpression) child, closeBracket);
            case 3 -> child == closeBracket ? this : create(target, openBracket, this.index, (GreenToken) child);
            default -> throw new IndexOutOfBoundsException(index);
        };
    }

    @Override
    public String toString() {
        return "IndexExpr[" + target + "[" + index + "]]";
    }
}
