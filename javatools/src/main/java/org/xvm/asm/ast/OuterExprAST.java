package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * An outer expression refers to a structural "parent" of the underlying expression.
 */
public class OuterExprAST<C>
        extends UnaryExprAST<C> {

    private int depth;

    OuterExprAST() {}

    public OuterExprAST(ExprAST<C> expr, int depth, C type) {
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
    public void read(DataInput in, ConstantResolver<C> res)
            throws IOException {
        super.read(in, res);

        depth = readMagnitude(in);
    }

    @Override
    public void write(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        super.write(out, res);

        writePackedLong(out, depth);
    }

    @Override
    public String dump() {
        return getExpr().dump() + (depth == 1 ? ".outer" : ".outer(" + depth + ")");
    }

    @Override
    public String toString() {
        return getExpr() + (depth == 1 ? ".outer" : ".outer(" + depth + ")");
    }
}