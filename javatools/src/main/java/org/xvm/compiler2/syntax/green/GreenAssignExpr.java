package org.xvm.compiler2.syntax.green;

import org.xvm.compiler2.syntax.SyntaxKind;

/**
 * An assignment expression: target = value (or +=, -=, etc.)
 */
public final class GreenAssignExpr extends GreenExpression {

    private final GreenExpression target;
    private final GreenToken operator;
    private final GreenExpression value;

    private GreenAssignExpr(GreenExpression target, GreenToken operator, GreenExpression value) {
        super(SyntaxKind.ASSIGNMENT_EXPRESSION,
                target.getFullWidth() + operator.getFullWidth() + value.getFullWidth());
        this.target = target;
        this.operator = operator;
        this.value = value;
    }

    public static GreenAssignExpr create(GreenExpression target, GreenToken operator,
                                         GreenExpression value) {
        return intern(new GreenAssignExpr(target, operator, value));
    }

    public static GreenAssignExpr create(GreenExpression target, GreenExpression value) {
        return create(target, GreenToken.create(SyntaxKind.ASSIGN, "="), value);
    }

    public GreenExpression getTarget() {
        return target;
    }

    public GreenToken getOperator() {
        return operator;
    }

    public SyntaxKind getOperatorKind() {
        return operator.getKind();
    }

    public GreenExpression getValue() {
        return value;
    }

    @Override
    public int getChildCount() {
        return 3;
    }

    @Override
    public GreenNode getChild(int index) {
        return switch (index) {
            case 0 -> target;
            case 1 -> operator;
            case 2 -> value;
            default -> throw new IndexOutOfBoundsException(index);
        };
    }

    @Override
    public GreenNode withChild(int index, GreenNode child) {
        return switch (index) {
            case 0 -> child == target ? this : create((GreenExpression) child, operator, value);
            case 1 -> child == operator ? this : create(target, (GreenToken) child, value);
            case 2 -> child == value ? this : create(target, operator, (GreenExpression) child);
            default -> throw new IndexOutOfBoundsException(index);
        };
    }

    @Override
    public String toString() {
        return "AssignExpr[" + target + " " + operator.getText() + " " + value + "]";
    }
}
