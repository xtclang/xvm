package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static org.xvm.asm.Op.CONSTANT_OFFSET;

import static org.xvm.asm.ast.BinaryAST.NodeType.ConstantExpr;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * An expression that yields a constant value.
 */
public class ConstantExprAST<C>
        extends ExprAST<C> {

    private           C value;
    private transient C type;

    ConstantExprAST() {}

    public ConstantExprAST(C value) {
        assert value != null;

        this.value = value;
    }

    public C getType() {
        return type;
    }

    public C getValue() {
        return value;
    }

    @Override
    public C getType(int i) {
        assert i == 0;
        return type;
    }

    @Override
    public NodeType nodeType() {
        return ConstantExpr;
    }

    @Override
    protected void readBody(DataInput in, ConstantResolver<C> res)
            throws IOException {
        value = res.getConstant(readMagnitude(in));
        type  = res.typeOf(value);
    }

    @Override
    public void prepareWrite(ConstantResolver<C> res) {
        value = res.register(value);
    }

    @Override
    protected void writeBody(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        writePackedLong(out, res.indexOf(value));
    }

    @Override
    protected void writeExpr(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        // instead of writing out the node type followed by the constant, we encode the constant
        // explicitly in a special range that indicates that the expression is a constant expr
        writePackedLong(out, CONSTANT_OFFSET - res.indexOf(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}