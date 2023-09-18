package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;


/**
 * A base class for expressions that delegate to an underlying expression.
 */
public abstract class DelegatingExprAST<C>
        extends ExprAST<C> {

    private ExprAST<C> expr;

    DelegatingExprAST() {}

    protected DelegatingExprAST(ExprAST<C> expr) {
        assert expr != null;
        this.expr = expr;
    }

    public ExprAST<C> getExpr() {
        return expr;
    }

    @Override
    public abstract NodeType nodeType();

    @Override
    public abstract C getType(int i);

    @Override
    protected void readBody(DataInput in, ConstantResolver<C> res)
            throws IOException {
        expr = readExprAST(in, res);
    }

    @Override
    public void prepareWrite(ConstantResolver<C> res) {
        expr.prepareWrite(res);
    }

    @Override
    protected void writeBody(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        expr.writeExpr(out, res);
    }
}