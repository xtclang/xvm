package org.xvm.compiler2.syntax.green;

import org.xvm.compiler2.syntax.SyntaxKind;

/**
 * A while statement: while (condition) body
 */
public final class GreenWhileStmt extends GreenStatement {

    private final GreenToken whileKeyword;
    private final GreenToken openParen;
    private final GreenExpression condition;
    private final GreenToken closeParen;
    private final GreenStatement body;

    private GreenWhileStmt(GreenToken whileKeyword, GreenToken openParen,
                          GreenExpression condition, GreenToken closeParen,
                          GreenStatement body) {
        super(SyntaxKind.WHILE_STATEMENT,
                whileKeyword.getFullWidth() + openParen.getFullWidth() +
                condition.getFullWidth() + closeParen.getFullWidth() + body.getFullWidth());
        this.whileKeyword = whileKeyword;
        this.openParen = openParen;
        this.condition = condition;
        this.closeParen = closeParen;
        this.body = body;
    }

    public static GreenWhileStmt create(GreenToken whileKeyword, GreenToken openParen,
                                        GreenExpression condition, GreenToken closeParen,
                                        GreenStatement body) {
        return intern(new GreenWhileStmt(whileKeyword, openParen, condition, closeParen, body));
    }

    public static GreenWhileStmt create(GreenExpression condition, GreenStatement body) {
        return create(
                GreenToken.create(SyntaxKind.KW_WHILE, "while"),
                GreenToken.create(SyntaxKind.LPAREN, "("),
                condition,
                GreenToken.create(SyntaxKind.RPAREN, ")"),
                body);
    }

    public GreenToken getWhileKeyword() {
        return whileKeyword;
    }

    public GreenToken getOpenParen() {
        return openParen;
    }

    public GreenExpression getCondition() {
        return condition;
    }

    public GreenToken getCloseParen() {
        return closeParen;
    }

    public GreenStatement getBody() {
        return body;
    }

    @Override
    public int getChildCount() {
        return 5;
    }

    @Override
    public GreenNode getChild(int index) {
        return switch (index) {
            case 0 -> whileKeyword;
            case 1 -> openParen;
            case 2 -> condition;
            case 3 -> closeParen;
            case 4 -> body;
            default -> throw new IndexOutOfBoundsException(index);
        };
    }

    @Override
    public GreenNode withChild(int index, GreenNode child) {
        return switch (index) {
            case 0 -> child == whileKeyword ? this : create((GreenToken) child, openParen, condition, closeParen, body);
            case 1 -> child == openParen ? this : create(whileKeyword, (GreenToken) child, condition, closeParen, body);
            case 2 -> child == condition ? this : create(whileKeyword, openParen, (GreenExpression) child, closeParen, body);
            case 3 -> child == closeParen ? this : create(whileKeyword, openParen, condition, (GreenToken) child, body);
            case 4 -> child == body ? this : create(whileKeyword, openParen, condition, closeParen, (GreenStatement) child);
            default -> throw new IndexOutOfBoundsException(index);
        };
    }

    @Override
    public String toString() {
        return "WhileStmt[" + condition + "]";
    }
}
