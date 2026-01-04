package org.xvm.compiler2.syntax.green;

import org.xvm.compiler2.syntax.SyntaxKind;

/**
 * A parenthesized expression.
 */
public final class GreenParenExpr extends GreenExpression {

    /**
     * The opening parenthesis.
     */
    private final GreenToken openParen;

    /**
     * The inner expression.
     */
    private final GreenExpression expression;

    /**
     * The closing parenthesis.
     */
    private final GreenToken closeParen;

    /**
     * Private constructor - use factory methods.
     */
    private GreenParenExpr(GreenToken openParen, GreenExpression expression, GreenToken closeParen) {
        super(SyntaxKind.PARENTHESIZED_EXPRESSION,
                openParen.getFullWidth() + expression.getFullWidth() + closeParen.getFullWidth());
        this.openParen = openParen;
        this.expression = expression;
        this.closeParen = closeParen;
    }

    /**
     * Create a parenthesized expression.
     *
     * @param openParen  the '(' token
     * @param expression the inner expression
     * @param closeParen the ')' token
     * @return the interned expression
     */
    public static GreenParenExpr create(GreenToken openParen, GreenExpression expression,
                                        GreenToken closeParen) {
        return intern(new GreenParenExpr(openParen, expression, closeParen));
    }

    /**
     * Create a parenthesized expression with default parens.
     *
     * @param expression the inner expression
     * @return the interned expression
     */
    public static GreenParenExpr create(GreenExpression expression) {
        return create(
                GreenToken.create(SyntaxKind.LPAREN, "("),
                expression,
                GreenToken.create(SyntaxKind.RPAREN, ")"));
    }

    /**
     * @return the opening parenthesis token
     */
    public GreenToken getOpenParen() {
        return openParen;
    }

    /**
     * @return the inner expression
     */
    public GreenExpression getExpression() {
        return expression;
    }

    /**
     * @return the closing parenthesis token
     */
    public GreenToken getCloseParen() {
        return closeParen;
    }

    @Override
    public int getChildCount() {
        return 3;
    }

    @Override
    public GreenNode getChild(int index) {
        return switch (index) {
            case 0 -> openParen;
            case 1 -> expression;
            case 2 -> closeParen;
            default -> throw new IndexOutOfBoundsException(index);
        };
    }

    @Override
    public GreenNode withChild(int index, GreenNode child) {
        return switch (index) {
            case 0 -> child == openParen ? this : create((GreenToken) child, expression, closeParen);
            case 1 -> child == expression ? this : create(openParen, (GreenExpression) child, closeParen);
            case 2 -> child == closeParen ? this : create(openParen, expression, (GreenToken) child);
            default -> throw new IndexOutOfBoundsException(index);
        };
    }

    @Override
    public String toString() {
        return "ParenExpr[(" + expression + ")]";
    }
}
