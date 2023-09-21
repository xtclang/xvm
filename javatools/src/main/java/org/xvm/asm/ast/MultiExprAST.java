package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Arrays;
import java.util.Objects;

import org.xvm.asm.constants.TypeConstant;

import static org.xvm.asm.ast.BinaryAST.NodeType.MultiExpr;


/**
 * A expression that represents multiple expressions.
 */
public class MultiExprAST
        extends ExprAST {

    private ExprAST[] exprs;

    MultiExprAST() {}

    public MultiExprAST(ExprAST[] exprs) {
        assert exprs != null && Arrays.stream(exprs).allMatch(Objects::nonNull);
        this.exprs = exprs;
    }

    public ExprAST[] getExprs() {
        return exprs; // note: caller must not modify returned array in any way
    }

    @Override
    public NodeType nodeType() {
        return MultiExpr;
    }

    @Override
    public int getCount() {
        return exprs.length;
    }

    @Override
    public TypeConstant getType(int i) {
        return exprs[i].getType(0);
    }

    @Override
    public boolean isAssignable() {
        return Arrays.stream(exprs).allMatch(ExprAST::isAssignable);
    }

    @Override
    protected void readBody(DataInput in, ConstantResolver res)
            throws IOException {
        exprs = readExprArray(in, res);
    }

    @Override
    public void prepareWrite(ConstantResolver res) {
        prepareASTArray(exprs, res);
    }

    @Override
    protected void writeBody(DataOutput out, ConstantResolver res)
            throws IOException {
        writeExprArray(exprs, out, res);
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