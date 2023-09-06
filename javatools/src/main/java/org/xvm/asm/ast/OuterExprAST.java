package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.ast.BinaryAST.ExprAST;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * An outer expression refers to an underlying expression on a "parent" AST.
 */
public class OuterExprAST<C>
        extends ExprAST<C> {

    private ExprAST<C> expr;
    private int        depth;

    OuterExprAST() {}

    public OuterExprAST(ExprAST<C> expr, int depth) {
        assert expr != null && depth > 0;

        this.expr  = expr;
        this.depth = depth;
    }

    public ExprAST<C> getExpr() {
        return expr;
    }

    public int getDepth() {
        return depth;
    }

    @Override
    public C getType(int i) {
        return expr.getType(i);
    }

    @Override
    public NodeType nodeType() {
        return NodeType.OuterExpr;
    }

    @Override
    public void read(DataInput in, ConstantResolver<C> res)
            throws IOException {
        expr  = readAST(in, res);
        depth = readMagnitude(in);
    }

    @Override
    public void prepareWrite(ConstantResolver<C> res) {
        expr.prepareWrite(res);
    }

    @Override
    public void write(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        out.writeByte(nodeType().ordinal());

        expr.writeExpr(out, res);
        writePackedLong(out, depth);
    }

    @Override
    public String dump() {
        return (depth == 1 ? "outer." : "outer(" + depth + ").") + expr.dump();
    }

    @Override
    public String toString() {
        return (depth == 1 ? "outer." : "outer(" + depth + ").") + expr;
    }
}