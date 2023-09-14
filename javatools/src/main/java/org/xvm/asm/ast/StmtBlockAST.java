package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Arrays;
import java.util.Objects;

import static org.xvm.asm.ast.BinaryAST.NodeType.None;
import static org.xvm.asm.ast.BinaryAST.NodeType.StmtBlock;
import static org.xvm.asm.ast.BinaryAST.NodeType.MultiStmt;

import static org.xvm.util.Handy.indentLines;


/**
 * Zero or more nested statements.
 */
public class StmtBlockAST<C>
        extends BinaryAST<C> {

    private BinaryAST<C>[] stmts;

    private final boolean  hasScope;

    StmtBlockAST(NodeType nodeType) {
        this.hasScope = switch (nodeType) {
            case StmtBlock -> true;
            case MultiStmt -> false;
            default -> throw new IllegalArgumentException("nodeType=" + nodeType);
        };
    }

    public StmtBlockAST(BinaryAST<C>[] stmts, Boolean hasScope) {
        assert stmts != null && Arrays.stream(stmts).allMatch(Objects::nonNull);
        this.stmts    = stmts;
        this.hasScope = hasScope && stmts.length > 0;
    }

    public boolean hasScope() {
        return hasScope;
    }

    public BinaryAST<C>[] getStmts() {
        return stmts; // note: caller must not modify returned array in any way
    }

    @Override
    public NodeType nodeType() {
        return hasScope ? StmtBlock : MultiStmt;
    }

    @Override
    protected void readBody(DataInput in, ConstantResolver<C> res)
            throws IOException {
        if (hasScope) {
            res.enter();
            stmts = readASTArray(in, res);
            res.exit();
        } else {
            stmts = readASTArray(in, res);
        }
    }

    @Override
    public void prepareWrite(ConstantResolver<C> res) {
        if (hasScope) {
            res.enter();
            prepareASTArray(stmts, res);
            res.exit();
        } else {
            prepareASTArray(stmts, res);
        }
    }

    @Override
    public void write(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        if (stmts.length == 0) {
            out.writeByte(None.ordinal());
        } else {
            super.write(out, res);
        }
    }

    @Override
    protected void writeBody(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        writeASTArray(stmts, out, res);
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