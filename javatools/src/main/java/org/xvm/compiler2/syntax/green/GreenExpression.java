package org.xvm.compiler2.syntax.green;

import org.xvm.compiler2.syntax.SyntaxKind;

/**
 * Base class for all expression nodes in the green tree.
 * <p>
 * Expressions are syntax constructs that evaluate to a value.
 * This is a sealed class - all concrete expression types must extend it.
 */
public abstract sealed class GreenExpression extends GreenNode
        permits GreenLiteralExpr, GreenNameExpr, GreenBinaryExpr, GreenUnaryExpr,
                GreenParenExpr, GreenInvokeExpr, GreenConditionalExpr, GreenAssignExpr,
                GreenMemberAccessExpr, GreenIndexExpr {

    /**
     * Construct an expression node.
     *
     * @param kind      the syntax kind (must be an expression kind)
     * @param fullWidth the total character width
     */
    protected GreenExpression(SyntaxKind kind, int fullWidth) {
        super(kind, fullWidth);
        assert kind.isExpression() : "Not an expression kind: " + kind;
    }

    @Override
    public <R> R accept(GreenVisitor<R> visitor) {
        return visitor.visitExpression(this);
    }
}
