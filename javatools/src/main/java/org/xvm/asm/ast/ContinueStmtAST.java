package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.ast.LanguageAST.StmtAST;

import static org.xvm.asm.ast.LanguageAST.NodeType.CONTINUE_STMT;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * A "continue" statement that either "falls through" in a switch/case block, or advances to the
 * start of the next iteration in a loop.
 */
public class ContinueStmtAST<C>
        extends StmtAST<C> {

    private int depth;

    ContinueStmtAST() {
        depth = -1;
    }

    public ContinueStmtAST(int depth) {
        assert depth >= 0 & depth < 1024;            // arbitrary limit to catch obvious math bugs
        this.depth = depth;
    }

    @Override
    public NodeType nodeType() {
        return CONTINUE_STMT;
    }

    /**
     * @return a value, either 0 to indicate that the continue applies to the first enclosing loop
     *         or switch statement, or non-zero n to indicate that the continue applies to the n-th
     *         "statement parent" of this statement, where 1 is this statement's enclosing statement
     */
    public int getDepth() {
        return depth;
    }

    @Override
    public void read(DataInput in, ConstantResolver<C> res)
            throws IOException {
        depth = readMagnitude(in);
    }

    @Override
    public void prepareWrite(ConstantResolver<C> res) {}

    @Override
    public void write(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        out.writeByte(nodeType().ordinal());

        writePackedLong(out, depth);
    }

    @Override
    public String toString() {
        return "continue" + (depth == 0 ? "" : " ^" + depth);
    }
}