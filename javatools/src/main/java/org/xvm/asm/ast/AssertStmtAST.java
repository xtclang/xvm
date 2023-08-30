package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * An "assert" statement.
 * REVIEW encode presence of interval and message as part of node type?
 */
public class AssertStmtAST<C>
        extends BinaryAST<C>
    {
    private ExprAST<C> cond;
    private ExprAST<C> interval; // could be null
    private ExprAST<C> message;  // could be null

    AssertStmtAST() {}

    public AssertStmtAST(ExprAST<C> cond, ExprAST<C> interval, ExprAST<C> message) {
        this.cond     = cond;
        this.interval = interval;
        this.message  = message;
    }

    public ExprAST<C> getCond()
        {
        return cond;
        }

    public ExprAST<C> getInterval()
        {
        return interval;
        }

    public ExprAST<C> getMessage()
        {
        return message;
        }

    @Override
    public NodeType nodeType() {
        return NodeType.AssertStmt;
    }

    @Override
    public void read(DataInput in, ConstantResolver<C> res)
            throws IOException {
        cond = readExprAST(in, res);

        int flags = readMagnitude(in);
        if ((flags & 1) != 0) {
            interval = readAST(in, res);
        }
        if ((flags & 2) != 0) {
            message = readAST(in, res);
        }
    }

    @Override
    public void prepareWrite(ConstantResolver<C> res) {
        prepareAST(cond, res);
        prepareAST(interval, res);
        prepareAST(message, res);
    }

    @Override
    public void write(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        out.writeByte(nodeType().ordinal());

        writeExprAST(cond, out, res);

        int flags = (interval == null ? 0 : 1)
                  | (message  == null ? 0 : 2);
        writePackedLong(out, flags);

        if (interval != null) {
            interval.write(out, res);
        }
        if (message != null) {
            message.write(out, res);
        }
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder("assert ");
        if (cond != null) {
            buf.append(cond.dump());
        }
        if (interval != null) {
            buf.append("\n:rnd(")
               .append(interval.dump())
               .append(')');
        }
        if (message != null) {
            buf.append("\nas ").append(message.dump());
        }
        return buf.toString();
    }
}