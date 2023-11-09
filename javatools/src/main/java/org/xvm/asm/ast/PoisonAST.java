package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.constants.TypeConstant;


/**
 * Place-holder that cannot be serialized.
 */
public final class PoisonAST
        extends ExprAST {

    static final PoisonAST INSTANCE = new PoisonAST();

    private PoisonAST() {}

    @Override
    public NodeType nodeType() {
        return NodeType.None;
    }

    @Override
    public int getCount() {
        return 0;
    }

    @Override
    public TypeConstant getType(int i) {
        throw new IllegalStateException();
    }

    @Override
    protected void readBody(DataInput in, ConstantResolver res)
            throws IOException {
        throw new IllegalStateException();
    }

    @Override
    public void prepareWrite(ConstantResolver res) {
        throw new IllegalStateException();
    }

    @Override
    protected void writeBody(DataOutput out, ConstantResolver res)
            throws IOException {
        throw new IllegalStateException();
    }

    @Override
    public String toString() {
        return "Poison";
    }
}