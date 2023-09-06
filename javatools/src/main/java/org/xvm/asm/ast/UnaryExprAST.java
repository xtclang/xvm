package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * An expressions that is based solely on the underlying expression, but may change its type.
 */
public abstract class UnaryExprAST<C>
        extends DelegatingExprAST<C>
    {
    private C type;

    UnaryExprAST() {}

    public UnaryExprAST(ExprAST<C> expr, C type) {
        super(expr);

        assert type != null;
        this.type = type;
    }

    @Override
    public C getType(int i) {
        assert i == 0;
        return type;
    }

    @Override
    public abstract NodeType nodeType();

    @Override
    public void read(DataInput in, ConstantResolver<C> res)
            throws IOException {
        super.read(in, res);

        type = res.getConstant(readMagnitude(in));
    }

    @Override
    public void prepareWrite(ConstantResolver<C> res) {
        super.prepareWrite(res);

        type = res.register(type);
    }

    @Override
    public void write(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        super.write(out, res);

        writePackedLong(out, res.indexOf(type));
    }
}