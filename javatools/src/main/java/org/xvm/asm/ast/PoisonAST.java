package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

/**
 * Place-holder that cannot be serialized.
 */
public class PoisonAST<C>
        extends ExprAST<C> {

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
    public C getType(int i) {
        throw new IllegalStateException();
    }

    @Override
    protected void readBody(DataInput in, ConstantResolver<C> res)
            throws IOException {
        throw new IllegalStateException();
    }

    @Override
    public void prepareWrite(ConstantResolver<C> res) {
        throw new IllegalStateException();
    }

    @Override
    protected void writeBody(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        throw new IllegalStateException();
    }

    @Override
    public String toString() {
        return "Poison";
    }
}