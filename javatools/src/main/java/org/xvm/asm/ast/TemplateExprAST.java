package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Arrays;
import java.util.Objects;
import org.xvm.asm.ast.LanguageAST.ExprAST;


/**
 * A string literal expression containing expressions that will be evaluated and concatenated with
 * the literal portions to produce a resulting string.
 */
public class TemplateExprAST<C>
        extends ExprAST<C> {

    private ExprAST<C>[] exprs;

    private transient C typeString;

    TemplateExprAST() {}

    public TemplateExprAST(ExprAST<C>[] exprs) {
        assert exprs != null && Arrays.stream(exprs).allMatch(Objects::nonNull);;

        this.exprs = exprs;
    }

    public ExprAST<C>[] getExprs() {
        return exprs;
    }

    @Override
    public C getType(int i) {
        assert i == 0;
        return typeString;
    }

    @Override
    public NodeType nodeType() {
        return NodeType.TemplateExpr;
    }

    @Override
    public void read(DataInput in, ConstantResolver<C> res)
            throws IOException {
        exprs = readExprArray(in, res);

        typeString = res.typeForName("String");
    }

    @Override
    public void prepareWrite(ConstantResolver<C> res) {
        prepareWriteASTArray(res, exprs);
    }

    @Override
    public void write(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        out.writeByte(nodeType().ordinal());

        writeASTArray(out, res, exprs);
    }

    @Override
    public String dump() {
        StringBuilder buf = new StringBuilder();
        for (int i = 0, c = exprs.length; i < c; i++) {
            if (i > 0) {
                buf.append(", ");
            }
            buf.append(exprs[i].dump());
        }
        return buf.toString();
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        for (int i = 0, c = exprs.length; i < c; i++) {
            if (i > 0) {
                buf.append(", ");
            }
            buf.append(exprs[i]);
        }
        return buf.toString();
    }
}