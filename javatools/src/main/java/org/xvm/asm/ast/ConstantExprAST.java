package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.ast.BinaryAST.ExprAST;

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
    public void read(DataInput in, ConstantResolver<C> res)
            throws IOException {
        value = res.getConstant(readMagnitude(in));
        type  = res.typeOf(value);
    }

    @Override
    protected void readExpr(DataInput in, ConstantResolver<C> res)
            throws IOException {
        // this should never be called; the "read" is done inline by the readExprAST() method
        throw new UnsupportedOperationException();
    }

    @Override
    public void prepareWrite(ConstantResolver<C> res) {
        value = res.register(value);
    }

    @Override
    public void write(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        out.writeByte(nodeType().ordinal());
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