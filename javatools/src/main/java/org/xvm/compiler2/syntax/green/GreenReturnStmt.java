package org.xvm.compiler2.syntax.green;

import org.xvm.compiler2.syntax.SyntaxKind;

/**
 * A return statement: return [expression];
 */
public final class GreenReturnStmt extends GreenStatement {

    private final GreenToken returnKeyword;
    private final GreenExpression expression; // may be null
    private final GreenToken semicolon;

    private GreenReturnStmt(GreenToken returnKeyword, GreenExpression expression,
                           GreenToken semicolon) {
        super(SyntaxKind.RETURN_STATEMENT,
                returnKeyword.getFullWidth() +
                (expression != null ? expression.getFullWidth() : 0) +
                semicolon.getFullWidth());
        this.returnKeyword = returnKeyword;
        this.expression = expression;
        this.semicolon = semicolon;
    }

    public static GreenReturnStmt create(GreenToken returnKeyword, GreenExpression expression,
                                         GreenToken semicolon) {
        return intern(new GreenReturnStmt(returnKeyword, expression, semicolon));
    }

    public static GreenReturnStmt create(GreenExpression expression) {
        return create(
                GreenToken.create(SyntaxKind.KW_RETURN, "return"),
                expression,
                GreenToken.create(SyntaxKind.SEMICOLON, ";"));
    }

    public static GreenReturnStmt createVoid() {
        return create(
                GreenToken.create(SyntaxKind.KW_RETURN, "return"),
                null,
                GreenToken.create(SyntaxKind.SEMICOLON, ";"));
    }

    public GreenToken getReturnKeyword() {
        return returnKeyword;
    }

    public GreenExpression getExpression() {
        return expression;
    }

    public boolean hasExpression() {
        return expression != null;
    }

    public GreenToken getSemicolon() {
        return semicolon;
    }

    @Override
    public int getChildCount() {
        return expression != null ? 3 : 2;
    }

    @Override
    public GreenNode getChild(int index) {
        if (expression != null) {
            return switch (index) {
                case 0 -> returnKeyword;
                case 1 -> expression;
                case 2 -> semicolon;
                default -> throw new IndexOutOfBoundsException(index);
            };
        } else {
            return switch (index) {
                case 0 -> returnKeyword;
                case 1 -> semicolon;
                default -> throw new IndexOutOfBoundsException(index);
            };
        }
    }

    @Override
    public GreenNode withChild(int index, GreenNode child) {
        if (expression != null) {
            return switch (index) {
                case 0 -> child == returnKeyword ? this : create((GreenToken) child, expression, semicolon);
                case 1 -> child == expression ? this : create(returnKeyword, (GreenExpression) child, semicolon);
                case 2 -> child == semicolon ? this : create(returnKeyword, expression, (GreenToken) child);
                default -> throw new IndexOutOfBoundsException(index);
            };
        } else {
            return switch (index) {
                case 0 -> child == returnKeyword ? this : create((GreenToken) child, null, semicolon);
                case 1 -> child == semicolon ? this : create(returnKeyword, null, (GreenToken) child);
                default -> throw new IndexOutOfBoundsException(index);
            };
        }
    }

    @Override
    public String toString() {
        return expression != null ? "ReturnStmt[" + expression + "]" : "ReturnStmt[]";
    }
}
