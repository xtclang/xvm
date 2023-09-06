package org.xvm.asm.ast;


/**
 * A short-circuiting expression for testing if a sub-expression is null.
 */
public class NotNullExprAST<C>
        extends UnaryExprAST<C> {

    NotNullExprAST() {}

    /**
     * Note: strictly speaking, we don't need the type, since it could be computed as
     * "expr.getType(0).removeNullable()", but we don't have that operation on the resolver yet
     */
    public NotNullExprAST(ExprAST<C> expr, C type) {
        super(expr, type);
    }

    @Override
    public boolean isAssignable() {
        return getExpr().isAssignable();
    }

    @Override
    public NodeType nodeType() {
        return NodeType.NotNullExpr;
    }

    @Override
    public String dump() {
        return getExpr().dump() + '?';
    }

    @Override
    public String toString() {
        return getExpr().toString() + '?';
    }
}