package org.xvm.asm.node;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Arrays;
import java.util.Objects;

import org.xvm.asm.node.LanguageNode.StatementNode;

import org.xvm.util.Handy;


/**
 * Zero or more nested statements.
 */
public class StatementBlock<C>
        extends StatementNode<C> {

    StatementBlock() {}

    public StatementBlock(StatementNode<C>[] stmts) {
        assert stmts != null && Arrays.stream(stmts).allMatch(Objects::nonNull);
        this.stmts = stmts;
    }

    StatementNode<C>[] stmts;

    @Override
    public int nodeType() {
        return STMT_BLOCK;
    }

    @Override
    public void read(DataInput in, ConstantResolver<C> res)
            throws IOException {
        int count = Handy.readMagnitude(in);
        StatementNode<C>[] stmts = new StatementNode[count];
        for (int i = 0; i < count; ++i) {
            stmts[i] = deserialize(in, res);
        }
        this.stmts = stmts;
    }

    @Override
    public void prepareWrite(ConstantResolver<C> res) {
        for (StatementNode child : stmts) {
            child.prepareWrite(res);
        }
    }

    @Override
    public void write(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        out.writeByte(nodeType());
        Handy.writePackedLong(out, stmts.length);
        for (StatementNode child : stmts) {
            child.write(out, res);
        }
    }

    @Override
    public String dump() {
        StringBuilder buf = new StringBuilder();
        buf.append("{");
        for (StatementNode child : stmts) {
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
