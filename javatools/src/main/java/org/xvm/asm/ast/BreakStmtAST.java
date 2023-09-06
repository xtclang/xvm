package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static org.xvm.asm.ast.BinaryAST.NodeType.BreakStmt;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * A "break" statement that either terminates a switch/case block, terminates a loop, or (if depth
 * is not 0) terminates an enclosing statement that is "depth" parent levels up from this AST node.
 */
public class BreakStmtAST<C>
        extends BinaryAST<C> {

    private int depth;

    BreakStmtAST() {
        depth = -1;
    }

    public BreakStmtAST(int depth) {
        assert depth >= 0 & depth < 1024;            // arbitrary limit to catch obvious math bugs
        this.depth = depth;
    }

    @Override
    public NodeType nodeType() {
        return BreakStmt;
    }

    /**
     * @return a value, either 0 to indicate that the break applies to the first enclosing loop
     *         or switch statement, or non-zero n to indicate that the break applies to the n-th
     *         "statement parent" of this statement, where 1 is this statement's enclosing statement
     */
    public int getDepth() {
        return depth;
    }

    @Override
    protected void readBody(DataInput in, ConstantResolver<C> res)
            throws IOException {
        depth = readMagnitude(in);
    }

    @Override
    public void prepareWrite(ConstantResolver<C> res) {}

    @Override
    protected void writeBody(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        writePackedLong(out, depth);
    }

    @Override
    public String toString() {
        return "break" + (depth == 0 ? "" : " ^" + depth);
    }
}