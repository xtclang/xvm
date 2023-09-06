package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Arrays;
import java.util.Objects;

import static org.xvm.asm.ast.BinaryAST.NodeType.None;
import static org.xvm.asm.ast.BinaryAST.NodeType.StmtBlock;

import static org.xvm.util.Handy.indentLines;


/**
 * Zero or more nested statements.
 */
public class StmtBlockAST<C>
        extends BinaryAST<C> {

    private BinaryAST<C>[] stmts;

    StmtBlockAST() {}

    public StmtBlockAST(BinaryAST<C>[] stmts) {
        assert stmts != null && Arrays.stream(stmts).allMatch(Objects::nonNull);
        this.stmts = stmts;
    }

    @Override
    public NodeType nodeType() {
        return StmtBlock;
    }

    public BinaryAST<C>[] getStmts() {
        return stmts; // note: caller must not modify returned array in any way
    }

    @Override
    public void read(DataInput in, ConstantResolver<C> res)
            throws IOException {
        res.enter();
        stmts = readASTArray(in, res);
        res.exit();
    }

    @Override
    public void prepareWrite(ConstantResolver<C> res) {
        res.enter();
        prepareASTArray(stmts, res);
        res.exit();
    }

    @Override
    public void write(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        if (stmts.length == 0) {
            out.writeByte(None.ordinal());
        } else {
            out.writeByte(nodeType().ordinal());
            writeASTArray(stmts, out, res);
        }
    }

    @Override
    public String dump() {
        if (stmts.length == 0) {
            return "{}";
        }

        StringBuilder buf = new StringBuilder();
        buf.append("{");
        for (BinaryAST child : stmts) {
            buf.append('\n')
               .append(indentLines(child.dump(), "  "));
        }
        buf.append("\n}");
        return buf.toString();
    }

    @Override
    public String toString() {
        if (stmts.length == 0) {
            return "{}";
        }

        return "{ ... " + stmts.length + " statements ... }";
    }
}