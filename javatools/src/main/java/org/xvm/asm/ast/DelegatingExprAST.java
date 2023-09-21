package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.constants.TypeConstant;


/**
 * A base class for expressions that delegate to an underlying expression.
 */
public abstract class DelegatingExprAST
        extends ExprAST {

    private ExprAST expr;

    DelegatingExprAST() {}

    protected DelegatingExprAST(ExprAST expr) {
        assert expr != null;
        this.expr = expr;
    }

    public ExprAST getExpr() {
        return expr;
    }

    @Override
    public abstract NodeType nodeType();

    @Override
    public abstract TypeConstant getType(int i);

    @Override
    protected void readBody(DataInput in, ConstantResolver res)
            throws IOException {
        expr = readExprAST(in, res);
    }

    @Override
    public void prepareWrite(ConstantResolver res) {
        expr.prepareWrite(res);
    }

    @Override
    protected void writeBody(DataOutput out, ConstantResolver res)
            throws IOException {
        expr.writeExpr(out, res);
    }
}