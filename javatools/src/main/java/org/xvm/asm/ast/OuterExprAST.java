package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.constants.TypeConstant;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * An outer expression refers to a structural "parent" of the underlying expression.
 */
public class OuterExprAST
        extends UnaryExprAST {

    private int depth;

    OuterExprAST() {}

    public OuterExprAST(ExprAST expr, int depth, TypeConstant type) {
        super(expr, type);

        assert depth > 0;
        this.depth = depth;
    }

    public int getDepth() {
        return depth;
    }

    @Override
    public NodeType nodeType() {
        return NodeType.OuterExpr;
    }

    @Override
    protected void readBody(DataInput in, ConstantResolver res)
            throws IOException {
        super.readBody(in, res);

        depth = readMagnitude(in);
    }

    @Override
    protected void writeBody(DataOutput out, ConstantResolver res)
            throws IOException {
        super.writeBody(out, res);

        writePackedLong(out, depth);
    }

    @Override
    public String toString() {
        return getExpr() + ".outer".repeat(Math.max(0, depth));
    }
}