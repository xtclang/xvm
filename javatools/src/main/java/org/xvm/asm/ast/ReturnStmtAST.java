package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Arrays;
import java.util.Objects;

import org.xvm.asm.ast.LanguageAST.StmtAST;

import static org.xvm.asm.ast.LanguageAST.NodeType.RETURN_STMT;

import static org.xvm.util.Handy.indentLines;


/**
 * Zero or more nested statements.
 */
public class ReturnStmtAST<C>
        extends StmtAST<C> {

    private ExprAST<C>[] exprs;

    public ReturnStmtAST() {
        this.exprs = NO_EXPRS;
    }

    public ReturnStmtAST(ExprAST<C>[] exprs) {
        assert exprs == null || Arrays.stream(exprs).allMatch(Objects::nonNull);
        this.exprs = exprs == null ? NO_EXPRS : exprs;
    }

    @Override
    public NodeType nodeType() {
        return RETURN_STMT;
    }

    public ExprAST<C>[] getExprs() {
        return exprs; // note: caller must not modify returned array in any way
    }

    @Override
    public void read(DataInput in, ConstantResolver<C> res)
            throws IOException {
        exprs = readExprArray(in, res);
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
        buf.append(this);
        for (ExprAST child : exprs) {
            buf.append('\n').append(indentLines(child.dump(), "  "));
        }
        return buf.toString();
    }

    @Override
    public String toString() {
        return nodeType().name() + ":" + exprs.length;
    }
}