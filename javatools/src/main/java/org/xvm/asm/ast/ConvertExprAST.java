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
        extends UnaryOpExprAST<C> {

    private C convMethod;

    ConvertExprAST() {}

    public ConvertExprAST(ExprAST<C> expr, C type, C convMethod) {
        super(expr, Operator.Convert, type);

        this.convMethod = convMethod;
    }

    public C getConvMethod() {
        return convMethod;
    }

    @Override
    public NodeType nodeType() {
        return NodeType.ConvertExpr;
    }

    @Override
    protected void readBody(DataInput in, ConstantResolver<C> res)
            throws IOException {
        super.readBody(in, res);

        convMethod = res.getConstant(readMagnitude(in));
    }

    @Override
    public void prepareWrite(ConstantResolver<C> res) {
        super.prepareWrite(res);

        convMethod = res.register(convMethod);
    }

    @Override
    protected void writeBody(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        super.writeBody(out, res);

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