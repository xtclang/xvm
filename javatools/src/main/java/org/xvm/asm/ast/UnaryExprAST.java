package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.constants.TypeConstant;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * An expressions that is based solely on the underlying expression, but may change its type.
 */
public abstract class UnaryExprAST
        extends DelegatingExprAST
    {
    private TypeConstant type;

    UnaryExprAST() {}

    public UnaryExprAST(ExprAST expr, TypeConstant type) {
        super(expr);

        assert type != null;
        this.type = type;
    }

    @Override
    public TypeConstant getType(int i) {
        assert i == 0;
        return type;
    }

    @Override
    public abstract NodeType nodeType();

    @Override
    protected void readBody(DataInput in, ConstantResolver res)
            throws IOException {
        super.readBody(in, res);

        type = (TypeConstant) res.getConstant(readMagnitude(in));
    }

    @Override
    public void prepareWrite(ConstantResolver res) {
        super.prepareWrite(res);

        type = (TypeConstant) res.register(type);
    }

    @Override
    protected void writeBody(DataOutput out, ConstantResolver res)
            throws IOException {
        super.writeBody(out, res);

        writePackedLong(out, res.indexOf(type));
    }
}