package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;

import org.xvm.asm.constants.TypeConstant;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * The Convert expressions.
 */
public class ConvertExprAST
        extends UnaryOpExprAST {

    private Constant convMethod;

    ConvertExprAST() {}

    public ConvertExprAST(ExprAST expr, TypeConstant type, Constant convMethod) {
        super(expr, Operator.Convert, type);

        this.convMethod = convMethod;
    }

    public Constant getConvMethod() {
        return convMethod;
    }

    @Override
    public NodeType nodeType() {
        return NodeType.ConvertExpr;
    }

    @Override
    protected void readBody(DataInput in, ConstantResolver res)
            throws IOException {
        super.readBody(in, res);

        convMethod = res.getConstant(readMagnitude(in));
    }

    @Override
    public void prepareWrite(ConstantResolver res) {
        super.prepareWrite(res);

        convMethod = res.register(convMethod);
    }

    @Override
    protected void writeBody(DataOutput out, ConstantResolver res)
            throws IOException {
        super.writeBody(out, res);

        writePackedLong(out, res.indexOf(convMethod));
    }

    @Override
    public String toString() {
        return '(' + super.toString() + ")." + convMethod + "()";
    }
}