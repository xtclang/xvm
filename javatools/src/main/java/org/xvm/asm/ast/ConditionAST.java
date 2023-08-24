package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Arrays;
import java.util.Objects;

import static org.xvm.util.Handy.indentLines;


/**
 * Condition that yields a Boolean value. May contain more than one condition. May contain variable
 * declaration and variable assignment statements. Used in if/while/for/etc.
 */
public class ConditionAST<C>
        extends LanguageAST<C> {

    private LanguageAST<C>[] conds;

    ConditionAST(DataInput in, ConstantResolver<C> res)
            throws IOException {
        read(in, res);
    }

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
        conds = readASTArray(in, res);
    }

    @Override
    public void prepareWrite(ConstantResolver<C> res) {
        prepareWriteASTArray(res, conds);
    }

    @Override
    public void write(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        writeASTArray(out, res, conds);
    }

    @Override
    public String dump() {
        StringBuilder buf = new StringBuilder();
        buf.append(this);
        for (LanguageAST cond : conds) {
            buf.append('\n').append(indentLines(cond.dump(), "  "));
        }
        return buf.toString();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + ":" + conds.length;
    }
}