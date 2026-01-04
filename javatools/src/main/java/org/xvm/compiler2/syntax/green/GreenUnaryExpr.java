package org.xvm.compiler2.syntax.green;

import org.xvm.compiler2.syntax.SyntaxKind;

/**
 * A unary expression (prefix or postfix operator with single operand).
 */
public final class GreenUnaryExpr extends GreenExpression {

    /**
     * The operator token.
     */
    private final GreenToken operator;

    /**
     * The operand expression.
     */
    private final GreenExpression operand;

    /**
     * True if this is a prefix operator (e.g., -x), false if postfix (e.g., x++).
     */
    private final boolean prefix;

    /**
     * Private constructor - use factory methods.
     */
    private GreenUnaryExpr(GreenToken operator, GreenExpression operand, boolean prefix) {
        super(SyntaxKind.UNARY_EXPRESSION,
                operator.getFullWidth() + operand.getFullWidth());
        this.operator = operator;
        this.operand = operand;
        this.prefix = prefix;
    }

    /**
     * Create a prefix unary expression.
     *
     * @param operator the operator token
     * @param operand  the operand
     * @return the interned expression
     */
    public static GreenUnaryExpr prefix(GreenToken operator, GreenExpression operand) {
        return intern(new GreenUnaryExpr(operator, operand, true));
    }

    /**
     * Create a postfix unary expression.
     *
     * @param operand  the operand
     * @param operator the operator token
     * @return the interned expression
     */
    public static GreenUnaryExpr postfix(GreenExpression operand, GreenToken operator) {
        return intern(new GreenUnaryExpr(operator, operand, false));
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
     * @return the operand expression
     */
    public GreenExpression getOperand() {
        return operand;
    }

    /**
     * @return true if prefix operator, false if postfix
     */
    public boolean isPrefix() {
        return prefix;
    }

    @Override
    public int getChildCount() {
        return 2;
    }

    @Override
    public GreenNode getChild(int index) {
        if (prefix) {
            return switch (index) {
                case 0 -> operator;
                case 1 -> operand;
                default -> throw new IndexOutOfBoundsException(index);
            };
        } else {
            return switch (index) {
                case 0 -> operand;
                case 1 -> operator;
                default -> throw new IndexOutOfBoundsException(index);
            };
        }
    }

    @Override
    public GreenNode withChild(int index, GreenNode child) {
        if (prefix) {
            return switch (index) {
                case 0 -> child == operator ? this : intern(new GreenUnaryExpr((GreenToken) child, operand, true));
                case 1 -> child == operand ? this : intern(new GreenUnaryExpr(operator, (GreenExpression) child, true));
                default -> throw new IndexOutOfBoundsException(index);
            };
        } else {
            return switch (index) {
                case 0 -> child == operand ? this : intern(new GreenUnaryExpr(operator, (GreenExpression) child, false));
                case 1 -> child == operator ? this : intern(new GreenUnaryExpr((GreenToken) child, operand, false));
                default -> throw new IndexOutOfBoundsException(index);
            };
        }
    }

    @Override
    public String toString() {
        if (prefix) {
            return "UnaryExpr[" + operator.getText() + operand + "]";
        } else {
            return "UnaryExpr[" + operand + operator.getText() + "]";
        }
    }
}
