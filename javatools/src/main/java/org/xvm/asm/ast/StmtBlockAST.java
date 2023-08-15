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

    StmtBlockAST() {}

    public StmtBlockAST(StmtAST<C>[] stmts) {
        assert stmts != null && Arrays.stream(stmts).allMatch(Objects::nonNull);
        this.stmts = stmts;
    }

    StmtAST<C>[] stmts;

    @Override
    public NodeType nodeType() {
        return STMT_BLOCK;
    }

    public StmtAST<C>[] getStmts() {
        // it would be nice if Java had a way of exposing container data without exposing mutability
        return stmts;
    }

    @Override
    public void read(DataInput in, ConstantResolver<C> res)
            throws IOException {
        int count = Handy.readMagnitude(in);
        StmtAST<C>[] stmts = new StmtAST[count];
        for (int i = 0; i < count; ++i) {
            stmts[i] = deserialize(in, res);
        }
        this.stmts = stmts;
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
        Handy.writePackedLong(out, stmts.length);
        for (StmtAST child : stmts) {
            child.write(out, res);
        }
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
