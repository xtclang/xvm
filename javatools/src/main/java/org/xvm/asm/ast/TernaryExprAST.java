package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.ast.LanguageAST.ExprAST;


/**
 * A base class for expressions that follow the pattern "expression operator expression".
 */
public class TernaryExprAST<C>
        extends ExprAST<C> {

    private ExprAST<C> cond;
    private ExprAST<C> exprThen;
    private ExprAST<C> exprElse;

    TernaryExprAST() {}

    public TernaryExprAST(ExprAST<C> cond, ExprAST<C> exprThen, ExprAST<C> exprElse) {
        assert cond != null && exprThen != null && exprElse != null;
        this.cond     = cond;
        this.exprThen = exprThen;
        this.exprElse = exprElse;
    }

    public ExprAST<C> getCond() {
        return cond;
    }

    public ExprAST<C> getThen() {
        return exprThen;
    }

    public ExprAST<C> getElse() {
        return exprElse;
    }

    @Override
    public C getType(int i) {
        return exprThen.getType(i);
    }

    @Override
    public NodeType nodeType() {
        return NodeType.TernaryExpr;
    }

    @Override
    public void read(DataInput in, ConstantResolver<C> res)
            throws IOException {
        cond     = deserialize(in, res);
        exprThen = deserialize(in, res);
        exprElse = deserialize(in, res);
    }

    @Override
    public void prepareWrite(ConstantResolver<C> res) {
        cond    .prepareWrite(res);
        exprThen.prepareWrite(res);
        exprElse.prepareWrite(res);
    }

    @Override
    public void write(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        out.writeByte(nodeType().ordinal());

        cond    .write(out, res);
        exprThen.write(out, res);
        exprElse.write(out, res);
    }

    @Override
    public String dump() {
        return cond.dump() + " ? " + exprThen.dump() + " : " + exprElse.dump();
    }

    @Override
    public String toString() {
        return cond + " ? " + exprThen + " : " + exprElse;
    }
}