package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.ast.BinaryAST.ExprAST;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * One of "Pack", "Unpack", "Convert", "ToInt", or "Trace" expressions.
 */
public class SyntheticExprAST<C>
        extends ExprAST<C> {

    private Operation  operation;
    private C          type;
    private ExprAST<C> underlyingExpr;

    public enum Operation {Pack, Unpack, Convert, ToInt, Trace}

    SyntheticExprAST() {}

    public SyntheticExprAST(Operation operation, C type, ExprAST<C> underlyingExpr) {
        assert operation != null && type != null && underlyingExpr != null;

        this.operation      = operation;
        this.type           = type;
        this.underlyingExpr = underlyingExpr;
    }

    public ExprAST<C> getUnderlyingExpr() {
        return underlyingExpr;
    }

    @Override
    public C getType(int i) {
        assert i == 0;
        return type;
    }

    @Override
    public NodeType nodeType() {
        return NodeType.SyntheticExpr;
    }

    @Override
    public void read(DataInput in, ConstantResolver<C> res)
            throws IOException {
        operation      = Operation.values()[readMagnitude(in)];
        type           = res.getConstant(readMagnitude(in));
        underlyingExpr = readAST(in, res);
    }

    @Override
    public void prepareWrite(ConstantResolver<C> res) {
        type = res.register(type);
        underlyingExpr.prepareWrite(res);
    }

    @Override
    public void write(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        out.writeByte(nodeType().ordinal());

        writePackedLong(out, operation.ordinal());
        writePackedLong(out, res.indexOf(type));
        underlyingExpr.write(out, res);
    }

    @Override
    public String dump() {
        return operation.toString() + ": " + underlyingExpr.dump();
    }

    @Override
    public String toString() {
        return operation + ": " + underlyingExpr;
    }
}