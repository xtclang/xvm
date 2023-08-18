package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Arrays;
import java.util.Objects;

import org.xvm.util.Handy;


/**
 * Condition that yields a Boolean value. May contain more than one condition. May contain variable
 * declaration and variable assignment statements. Used in if/while/for/etc.
 */
public class ConditionAST<C>
        extends LanguageAST<C> {

    private LanguageAST<C>[] conds;

    ConditionAST() {}

    public ConditionAST(LanguageAST<C>[] conds) {
        assert conds == null || Arrays.stream(conds).allMatch(Objects::nonNull);
        this.conds = conds == null ? NO_ASTS : conds;
    }

    @Override
    public NodeType nodeType() {
        // this doesn't get its own NodeType; it is subsumed by its parent node
        throw new UnsupportedOperationException();
    }

    public LanguageAST<C>[] getConditions() {
        return conds; // note: caller must not modify returned array in any way
    }

    @Override
    public void read(DataInput in, ConstantResolver<C> res)
            throws IOException {
        int count = Handy.readMagnitude(in);
        LanguageAST<C>[] conds = count == 0 ? NO_EXPRS : new LanguageAST[count];
        for (int i = 0; i < count; ++i) {
            conds[i] = deserialize(in, res);
        }
        this.conds = conds;
    }

    @Override
    public void prepareWrite(ConstantResolver<C> res) {
        for (LanguageAST cond : conds) {
            cond.prepareWrite(res);
        }
    }

    @Override
    public void write(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        Handy.writePackedLong(out, conds.length);
        for (LanguageAST cond : conds) {
            cond.write(out, res);
        }
    }

    @Override
    public String dump() {
        StringBuilder buf = new StringBuilder();
        buf.append(this);
        for (LanguageAST cond : conds) {
            buf.append('\n').append(Handy.indentLines(cond.dump(), "  "));
        }
        return buf.toString();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + ":" + conds.length;
    }
}
