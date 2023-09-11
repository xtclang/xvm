package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Arrays;
import java.util.Objects;

import static org.xvm.asm.ast.BinaryAST.NodeType.MultiExpr;


/**
 * A expression that represents multiple expressions.
 */
public class MultiExprAST<C>
        extends ExprAST<C> {

    private ExprAST<C>[] exprs;

    MultiExprAST() {}

    public MultiExprAST(ExprAST<C>[] values) {
        assert values != null && Arrays.stream(values).allMatch(Objects::nonNull);
        this.exprs = values;
    }

    public ExprAST<C>[] getExprs() {
        return exprs; // note: caller must not modify returned array in any way
    }

    @Override
    public C getType(int i) {
        return exprs[i].getType(0);
    }

    @Override
    public NodeType nodeType() {
        return MultiExpr;
    }

    @Override
    protected void readBody(DataInput in, ConstantResolver<C> res)
            throws IOException {
        exprs = readExprArray(in, res);
    }

    @Override
    public void prepareWrite(ConstantResolver<C> res) {
        prepareASTArray(exprs, res);
    }

    @Override
    protected void writeBody(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        writeExprArray(exprs, out, res);
    }

    @Override
    public String dump() {
        StringBuilder buf = new StringBuilder();
        buf.append('(');
        for (ExprAST value : exprs) {
            buf.append(value.dump()).append(", ");
        }
        buf.append(')');
        return buf.toString();
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append('(');
        for (ExprAST value : exprs) {
            buf.append(value.toString()).append(", ");
        }
        buf.append(')');
        return buf.toString();
    }
}