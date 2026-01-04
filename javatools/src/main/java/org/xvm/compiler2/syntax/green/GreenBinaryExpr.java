package org.xvm.compiler2.syntax.green;

import org.xvm.compiler2.syntax.SyntaxKind;

/**
 * A binary expression (left operator right).
 */
public final class GreenBinaryExpr extends GreenExpression {

    /**
     * The left operand.
     */
    private final GreenExpression left;

    /**
     * The operator token.
     */
    private final GreenToken operator;

    /**
     * The right operand.
     */
    private final GreenExpression right;

    /**
     * Private constructor - use factory methods.
     */
    private GreenBinaryExpr(GreenExpression left, GreenToken operator, GreenExpression right) {
        super(SyntaxKind.BINARY_EXPRESSION,
                left.getFullWidth() + operator.getFullWidth() + right.getFullWidth());
        this.left = left;
        this.operator = operator;
        this.right = right;
    }

    /**
     * Create a binary expression.
     *
     * @param left     the left operand
     * @param operator the operator token
     * @param right    the right operand
     * @return the interned expression
     */
    public static GreenBinaryExpr create(GreenExpression left, GreenToken operator,
                                         GreenExpression right) {
        return intern(new GreenBinaryExpr(left, operator, right));
    }

    /**
     * @return the left operand
     */
    public GreenExpression getLeft() {
        return left;
    }

    /**
     * @return the operator token
     */
    public GreenToken getOperator() {
        return operator;
    }

    /**
     * @return the operator syntax kind
     */
    public SyntaxKind getOperatorKind() {
        return operator.getKind();
    }

    /**
     * @return the right operand
     */
    public GreenExpression getRight() {
        return right;
    }

    @Override
    public int getChildCount() {
        return 3;
    }

    @Override
    public GreenNode getChild(int index) {
        return switch (index) {
            case 0 -> left;
            case 1 -> operator;
            case 2 -> right;
            default -> throw new IndexOutOfBoundsException(index);
        };
    }

    @Override
    public GreenNode withChild(int index, GreenNode child) {
        return switch (index) {
            case 0 -> child == left ? this : create((GreenExpression) child, operator, right);
            case 1 -> child == operator ? this : create(left, (GreenToken) child, right);
            case 2 -> child == right ? this : create(left, operator, (GreenExpression) child);
            default -> throw new IndexOutOfBoundsException(index);
        };
    }

    @Override
    public String toString() {
        return "BinaryExpr[" + left + " " + operator.getText() + " " + right + "]";
    }
}
