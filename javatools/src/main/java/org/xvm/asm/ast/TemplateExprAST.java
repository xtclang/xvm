package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Arrays;
import java.util.Objects;

import org.xvm.asm.constants.TypeConstant;


/**
 * A string literal expression containing expressions that will be evaluated and concatenated with
 * the literal portions to produce a resulting string.
 */
public class TemplateExprAST
        extends ExprAST {

    private ExprAST[] exprs;

    private transient TypeConstant typeString;

    TemplateExprAST() {}

    public TemplateExprAST(ExprAST[] exprs) {
        assert exprs != null && Arrays.stream(exprs).allMatch(Objects::nonNull);;

        this.exprs = exprs;
    }

    public ExprAST[] getExprs() {
        return exprs;
    }

    @Override
    public TypeConstant getType(int i) {
        assert i == 0;
        return typeString;
    }

    @Override
    public NodeType nodeType() {
        return NodeType.TemplateExpr;
    }

    @Override
    protected void readBody(DataInput in, ConstantResolver res)
            throws IOException {
        exprs = readExprArray(in, res);

        typeString = res.typeForName("String");
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
        buf.append(exprs[0]);
        for (int i = 1, c = exprs.length; i < c; i++) {
            buf.append(" + ")
               .append(exprs[i]);
        }
        return buf.toString();
    }
}