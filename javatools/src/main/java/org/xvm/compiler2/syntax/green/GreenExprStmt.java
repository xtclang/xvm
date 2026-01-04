package org.xvm.compiler2.syntax.green;

import org.xvm.compiler2.syntax.SyntaxKind;

/**
 * An expression statement: expression;
 */
public final class GreenExprStmt extends GreenStatement {

    private final GreenExpression expression;
    private final GreenToken semicolon;

    private GreenExprStmt(GreenExpression expression, GreenToken semicolon) {
        super(SyntaxKind.EXPRESSION_STATEMENT,
                expression.getFullWidth() + semicolon.getFullWidth());
        this.expression = expression;
        this.semicolon = semicolon;
    }

    public static GreenExprStmt create(GreenExpression expression, GreenToken semicolon) {
        return intern(new GreenExprStmt(expression, semicolon));
    }

    public static GreenExprStmt create(GreenExpression expression) {
        return create(expression, GreenToken.create(SyntaxKind.SEMICOLON, ";"));
    }

    public GreenExpression getExpression() {
        return expression;
    }

    public GreenToken getSemicolon() {
        return semicolon;
    }

    @Override
    public int getChildCount() {
        return 2;
    }

    @Override
    public GreenNode getChild(int index) {
        return switch (index) {
            case 0 -> expression;
            case 1 -> semicolon;
            default -> throw new IndexOutOfBoundsException(index);
        };
    }

    @Override
    public GreenNode withChild(int index, GreenNode child) {
        return switch (index) {
            case 0 -> child == expression ? this : create((GreenExpression) child, semicolon);
            case 1 -> child == semicolon ? this : create(expression, (GreenToken) child);
            default -> throw new IndexOutOfBoundsException(index);
        };
    }

    @Override
    public String toString() {
        return "ExprStmt[" + expression + "]";
    }
}
