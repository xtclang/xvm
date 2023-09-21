package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.constants.TypeConstant;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * An "is" expression. It differs from {@link RelOpExprAST} that it may have two return types, where
 * the first one is always Boolean.
 */
public class IsExprAST
        extends BiExprAST {

    private TypeConstant typeOfType;   // could be null (TODO CP remove)
    private transient TypeConstant booleanType;  // TODO CP remove

    IsExprAST() {}

    public IsExprAST(ExprAST expr1, ExprAST expr2, TypeConstant typeOfType) {
        super(expr1, Operator.Is, expr2);

        this.typeOfType = typeOfType;
    }

    @Override
    public int getCount() {
        return 2;
    }

    @Override
    public TypeConstant getType(int i) {
        switch (i) {
        case 0:
            return booleanType;

        case 1:
            if (typeOfType != null) {
                return typeOfType;
            }
            // fall through
        default:
            throw new ArrayIndexOutOfBoundsException();
        }
    }

    @Override
    public NodeType nodeType() {
        return NodeType.DivRemExpr;
    }

    @Override
    protected void readBody(DataInput in, ConstantResolver res)
            throws IOException {
        super.readBody(in, res);

        if (readMagnitude(in) != 0) {
            typeOfType = (TypeConstant) res.getConstant(readMagnitude(in));
        }
        booleanType = res.typeForName("Boolean");
    }

    @Override
    public void prepareWrite(ConstantResolver res) {
        super.prepareWrite(res);

        if (typeOfType != null) {
            typeOfType = (TypeConstant) res.register(typeOfType);
        }
    }

    @Override
    protected void writeBody(DataOutput out, ConstantResolver res)
            throws IOException {
        super.writeBody(out, res);

        if (typeOfType == null) {
            writePackedLong(out, 0);
        } else {
            writePackedLong(out, 1);
            writePackedLong(out, res.indexOf(typeOfType));
        }
    }

    @Override
    public String toString() {
        return getExpr1() + ".is(" + getExpr2() + ')';
    }
}