package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * The Convert expressions.
 */
public class ConvertExprAST<C>
        extends SyntheticExprAST<C> {

    private C convMethod;

    ConvertExprAST() {}

    public ConvertExprAST(C type, ExprAST<C> underlyingExpr, C convMethod) {
        super(Operation.Convert, type, underlyingExpr);

        this.convMethod = convMethod;
    }

    @Override
    public NodeType nodeType() {
        return NodeType.ConvertExpr;
    }

    @Override
    public void read(DataInput in, ConstantResolver<C> res)
            throws IOException {
        super.read(in, res);

        convMethod = res.getConstant(readMagnitude(in));
    }

    @Override
    public void prepareWrite(ConstantResolver<C> res) {
        super.prepareWrite(res);

        convMethod = res.register(convMethod);
    }

    @Override
    public void write(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        super.write(out, res);

        writePackedLong(out, res.indexOf(convMethod));
    }

    @Override
    public String dump() {
        return super.dump() + "->" + convMethod;
    }

    @Override
    public String toString() {
        return super.toString() + "->" + convMethod;
    }
}