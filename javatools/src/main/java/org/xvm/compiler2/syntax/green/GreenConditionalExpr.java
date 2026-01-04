package org.xvm.compiler2.syntax.green;

import org.xvm.compiler2.syntax.SyntaxKind;

/**
 * A conditional (ternary) expression: condition ? thenExpr : elseExpr
 */
public final class GreenConditionalExpr extends GreenExpression {

    private final GreenExpression condition;
    private final GreenToken questionMark;
    private final GreenExpression thenExpr;
    private final GreenToken colon;
    private final GreenExpression elseExpr;

    private GreenConditionalExpr(GreenExpression condition, GreenToken questionMark,
                                 GreenExpression thenExpr, GreenToken colon,
                                 GreenExpression elseExpr) {
        super(SyntaxKind.CONDITIONAL_EXPRESSION,
                condition.getFullWidth() + questionMark.getFullWidth() +
                thenExpr.getFullWidth() + colon.getFullWidth() + elseExpr.getFullWidth());
        this.condition = condition;
        this.questionMark = questionMark;
        this.thenExpr = thenExpr;
        this.colon = colon;
        this.elseExpr = elseExpr;
    }

    public static GreenConditionalExpr create(GreenExpression condition, GreenToken questionMark,
                                              GreenExpression thenExpr, GreenToken colon,
                                              GreenExpression elseExpr) {
        return intern(new GreenConditionalExpr(condition, questionMark, thenExpr, colon, elseExpr));
    }

    public static GreenConditionalExpr create(GreenExpression condition, GreenExpression thenExpr,
                                              GreenExpression elseExpr) {
        return create(condition,
                GreenToken.create(SyntaxKind.COND, "?"),
                thenExpr,
                GreenToken.create(SyntaxKind.COLON, ":"),
                elseExpr);
    }

    public GreenExpression getCondition() {
        return condition;
    }

    public GreenToken getQuestionMark() {
        return questionMark;
    }

    public GreenExpression getThenExpr() {
        return thenExpr;
    }

    public GreenToken getColon() {
        return colon;
    }

    public GreenExpression getElseExpr() {
        return elseExpr;
    }

    @Override
    public int getChildCount() {
        return 5;
    }

    @Override
    public GreenNode getChild(int index) {
        return switch (index) {
            case 0 -> condition;
            case 1 -> questionMark;
            case 2 -> thenExpr;
            case 3 -> colon;
            case 4 -> elseExpr;
            default -> throw new IndexOutOfBoundsException(index);
        };
    }

    @Override
    public GreenNode withChild(int index, GreenNode child) {
        return switch (index) {
            case 0 -> child == condition ? this : create((GreenExpression) child, questionMark, thenExpr, colon, elseExpr);
            case 1 -> child == questionMark ? this : create(condition, (GreenToken) child, thenExpr, colon, elseExpr);
            case 2 -> child == thenExpr ? this : create(condition, questionMark, (GreenExpression) child, colon, elseExpr);
            case 3 -> child == colon ? this : create(condition, questionMark, thenExpr, (GreenToken) child, elseExpr);
            case 4 -> child == elseExpr ? this : create(condition, questionMark, thenExpr, colon, (GreenExpression) child);
            default -> throw new IndexOutOfBoundsException(index);
        };
    }

    @Override
    public String toString() {
        return "ConditionalExpr[" + condition + " ? " + thenExpr + " : " + elseExpr + "]";
    }
}
