package org.xvm.asm.ast;


import org.xvm.asm.constants.TypeConstant;


/**
 * The Narrowed expression refers to an underlying expression with a narrowed type.
 */
public class NarrowedExprAST
        extends UnaryExprAST {

    NarrowedExprAST() {}

    public NarrowedExprAST(ExprAST expr, TypeConstant type) {
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
    public String toString() {
        return getExpr() + ".as(" + getType(0).getValueString() + ')';
    }
}