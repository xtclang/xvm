package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.constants.TypeConstant;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * A "throw" expression.
 */
public class ThrowExprAST
        extends ExprAST
    {
    private ExprAST throwable;
    private ExprAST message; // could be null
    private transient TypeConstant type;

    ThrowExprAST() {}

    public ThrowExprAST(TypeConstant type, ExprAST throwable, ExprAST message) {
        assert type != null && throwable != null;

        this.type      = type;
        this.throwable = throwable;
        this.message   = message;
    }

    public ExprAST getThrowable()
        {
        return throwable;
        }

    public ExprAST getMessage()
        {
        return message;
        }

    @Override
    public NodeType nodeType() {
        return NodeType.ThrowExpr;
    }

    @Override
    public TypeConstant getType(int i) {
        assert i == 0;
        return type;
    }

    @Override
    protected void readBody(DataInput in, ConstantResolver res)
            throws IOException {
        type      = res.typeForName("Object"); // TODO "never" type
        throwable = readExprAST(in, res);
        if (readMagnitude(in) > 0) {
            message = readExprAST(in, res);
        }
    }

    @Override
    public void prepareWrite(ConstantResolver res) {
        throwable.prepareWrite(res);
        if (message != null) {
            message.prepareWrite(res);
        }
    }

    @Override
    protected void writeBody(DataOutput out, ConstantResolver res)
            throws IOException {
        throwable.writeExpr(out, res);
        if (message == null) {
            writePackedLong(out, 0);
        } else {
            writePackedLong(out, 1);
            message.writeExpr(out, res);
        }
    }

    @Override
    public String toString() {
        // TODO
        return "throw " + throwable + (message == null ? "" : " (" + message + ')');
    }
}