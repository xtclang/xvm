package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.Constant.Format;

import org.xvm.asm.constants.TypeConstant;

import static org.xvm.asm.Op.CONSTANT_OFFSET;

import static org.xvm.asm.ast.BinaryAST.NodeType.ConstantExpr;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * An expression that yields a constant value.
 */
public class ConstantExprAST
        extends ExprAST {

    private Constant value;

    ConstantExprAST() {}

    public ConstantExprAST(Constant value) {
        assert value != null;

        this.value = value;
    }

    public Constant getValue() {
        return value;
    }

    @Override
    public NodeType nodeType() {
        return ConstantExpr;
    }

    @Override
    public TypeConstant getType(int i) {
        assert i == 0;
        return value.getType();
    }

    @Override
    public boolean isAssignable() {
        return value.getFormat() == Format.Any;
    }

    @Override
    protected void readBody(DataInput in, ConstantResolver res)
            throws IOException {
        value = res.getConstant(readMagnitude(in));
    }

    @Override
    public void prepareWrite(ConstantResolver res) {
        value = res.register(value);
    }

    @Override
    protected void writeBody(DataOutput out, ConstantResolver res)
            throws IOException {
        writePackedLong(out, res.indexOf(value));
    }

    @Override
    protected void writeExpr(DataOutput out, ConstantResolver res)
            throws IOException {
        // instead of writing out the node type followed by the constant, we encode the constant
        // explicitly in a special range that indicates that the expression is a constant expr
        writePackedLong(out, CONSTANT_OFFSET - res.indexOf(value));
    }

    @Override
    public String toString() {
        return value.getValueString();
    }
}