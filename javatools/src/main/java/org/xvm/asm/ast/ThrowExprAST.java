package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * A "throw" expression.
 */
public class ThrowExprAST<C>
        extends ExprAST<C>
    {
    private C          type;
    private ExprAST<C> throwable;
    private ExprAST<C> message; // could be null

    ThrowExprAST() {}

    public ThrowExprAST(C type, ExprAST<C> throwable, ExprAST<C> message) {
        assert type != null && throwable != null;

        this.type      = type;
        this.throwable = throwable;
        this.message   = message;
    }

    public ExprAST<C> getThrowable()
        {
        return throwable;
        }

    public ExprAST<C> getMessage()
        {
        return message;
        }

    @Override
    public NodeType nodeType() {
        return NodeType.ThrowExpr;
    }

    @Override
    public C getType(int i) {
        assert i == 0;
        return type;
    }

    @Override
    protected void readBody(DataInput in, ConstantResolver<C> res)
            throws IOException {
        type      = res.getConstant(readMagnitude(in));
        throwable = readExprAST(in, res);
        if (readMagnitude(in) > 0) {
            message = readExprAST(in, res);
        }
    }

    @Override
    public void prepareWrite(ConstantResolver<C> res) {
        type = res.register(type);
        throwable.prepareWrite(res);
        if (message != null) {
            message.prepareWrite(res);
        }
    }

    @Override
    protected void writeBody(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        writePackedLong(out, res.indexOf(type));
        throwable.writeExpr(out, res);
        if (message == null) {
            writePackedLong(out, 0);
        } else {
            writePackedLong(out, 1);
            message.writeExpr(out, res);
        }
    }

    @Override
    public String dump() {
        return "throw (" + type + ") " + throwable.dump() +
                (message == null ? "" : " (" + message.dump() + ')');
    }

    @Override
    public String toString() {
        return "throw " + throwable + (message == null ? "" : " (" + message + ')');
    }
}