package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.constants.TypeConstant;


/**
 * A base class for expressions that follow the pattern "expression operator expression".
 */
public class TernaryExprAST
        extends ExprAST {

    private ExprAST cond;
    private ExprAST exprThen;
    private ExprAST exprElse;

    TernaryExprAST() {}

    public TernaryExprAST(ExprAST cond, ExprAST exprThen, ExprAST exprElse) {
        assert cond != null && exprThen != null && exprElse != null;
        this.cond     = cond;
        this.exprThen = exprThen;
        this.exprElse = exprElse;
    }

    public ExprAST getCond() {
        return cond;
    }

    public ExprAST getThen() {
        return exprThen;
    }

    public ExprAST getElse() {
        return exprElse;
    }

    @Override
    public NodeType nodeType() {
        return NodeType.TernaryExpr;
    }

    @Override
    public int getCount() {
        // there are three scenarios when the counts may not be the same:
        // - a Throw
        // - a conditional False (which is not currently possible to check here)
        // - a Ternary expression containing any of these three
        return Math.max(exprThen.getCount(), exprElse.getCount());
    }

    @Override
    public TypeConstant getType(int i) {
        return i < exprThen.getCount() ? exprThen.getType(i) : exprElse.getType(i);
    }

    @Override
    public boolean isAssignable() {
        return exprThen.isAssignable() && exprElse.isAssignable();
    }

    @Override
    protected void readBody(DataInput in, ConstantResolver res)
            throws IOException {
        cond     = readExprAST(in, res);
        exprThen = readExprAST(in, res);
        exprElse = readExprAST(in, res);
    }

    @Override
    public void prepareWrite(ConstantResolver res) {
        cond    .prepareWrite(res);
        exprThen.prepareWrite(res);
        exprElse.prepareWrite(res);
    }

    @Override
    protected void writeBody(DataOutput out, ConstantResolver res)
            throws IOException {
        cond    .writeExpr(out, res);
        exprThen.writeExpr(out, res);
        exprElse.writeExpr(out, res);
    }

    @Override
    public String toString() {
        return cond + " ? " + exprThen + " : " + exprElse;
    }
}