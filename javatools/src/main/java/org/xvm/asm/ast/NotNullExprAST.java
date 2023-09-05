package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.ast.BinaryAST.ExprAST;


/**
 * A short-circuiting expression for testing if a sub-expression is null.
 */
public class NotNullExprAST<C>
        extends ExprAST<C> {

    private ExprAST<C> expr;

    NotNullExprAST() {}

    public NotNullExprAST(ExprAST<C> expr) {
        assert expr != null;
        this.expr = expr;
    }

    public ExprAST<C> getExpr() {
        return expr;
    }

    @Override
    public C getType(int i) {
        assert i == 0;
        return expr.getType(i);
    }

    @Override
    public boolean isAssignable() {
        return expr.isAssignable();
    }

    @Override
    public NodeType nodeType() {
        return NodeType.NotNullExpr;
    }

    @Override
    public void read(DataInput in, ConstantResolver<C> res)
            throws IOException {
        expr = readExprAST(in, res);
    }

    @Override
    public void prepareWrite(ConstantResolver<C> res) {
        expr.prepareWrite(res);
    }

    @Override
    public void write(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        out.writeByte(nodeType().ordinal());

        expr.writeExpr(out, res);
    }

    @Override
    public String dump() {
        return expr.dump() + '?';
    }

    @Override
    public String toString() {
        return expr.toString() + '?';
    }
}