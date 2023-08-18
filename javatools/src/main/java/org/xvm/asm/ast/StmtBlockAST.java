package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Arrays;
import java.util.Objects;

import org.xvm.asm.ast.LanguageAST.StmtAST;

import org.xvm.util.Handy;

import static org.xvm.asm.ast.LanguageAST.NodeType.STMT_BLOCK;


/**
 * Zero or more nested statements.
 */
public class StmtBlockAST<C>
        extends StmtAST<C> {

    private StmtAST<C>[] stmts;

    StmtBlockAST() {}

    public StmtBlockAST(StmtAST<C>[] stmts) {
        assert stmts != null && Arrays.stream(stmts).allMatch(Objects::nonNull);
        this.stmts = stmts;
    }

    @Override
    public NodeType nodeType() {
        return STMT_BLOCK;
    }

    public StmtAST<C>[] getStmts() {
        return stmts; // note: caller must not modify returned array in any way
    }

    @Override
    public void read(DataInput in, ConstantResolver<C> res)
            throws IOException {
        this.stmts = readStmtArray(in, res);
    }

    @Override
    public void prepareWrite(ConstantResolver<C> res) {
        for (StmtAST child : stmts) {
            child.prepareWrite(res);
        }
    }

    @Override
    public void write(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        out.writeByte(nodeType().ordinal());
        writeASTArray(out, res, stmts);
    }

    @Override
    public String dump() {
        StringBuilder buf = new StringBuilder();
        buf.append("{");
        for (StmtAST child : stmts) {
            buf.append('\n')
               .append(Handy.indentLines(child.dump(), "  "));
        }
        buf.append("\n}");
        return buf.toString();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + stmts.length + "]";
    }
}
