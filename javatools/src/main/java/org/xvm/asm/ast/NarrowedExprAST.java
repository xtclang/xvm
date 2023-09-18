package org.xvm.asm.ast;


/**
 * The Narrowed expression refers to an underlying expression with a narrowed type.
 */
public class NarrowedExprAST<C>
        extends UnaryExprAST<C> {

    NarrowedExprAST() {}

    public NarrowedExprAST(ExprAST<C> expr, C type) {
        super(expr, type);
    }

    @Override
    public NodeType nodeType() {
        return NodeType.NarrowedExpr;
    }

    @Override
    public boolean isAssignable() {
        return getExpr().isAssignable();
    }

    @Override
    public String dump() {
        return getExpr().dump() + ".as(" + getType(0) + ")";
    }

    @Override
    public String toString() {
        return getExpr() + ".as(" + getType(0) + ")";
    }
}