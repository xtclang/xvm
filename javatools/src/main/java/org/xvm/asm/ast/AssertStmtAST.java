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
public class AssertStmtAST
        extends BinaryAST
    {
    private ExprAST cond;     // could be null
    private ExprAST interval; // could be null
    private ExprAST message;  // could be null

    AssertStmtAST() {}

    public AssertStmtAST(ExprAST cond, ExprAST interval, ExprAST message) {
        this.cond     = cond;
        this.interval = interval;
        this.message  = message;
    }

    public ExprAST getCond()
        {
        return cond;
        }

    public ExprAST getInterval()
        {
        return interval;
        }

    public ExprAST getMessage()
        {
        return message;
        }

    @Override
    public NodeType nodeType() {
        return NodeType.AssertStmt;
    }

    @Override
    protected void readBody(DataInput in, ConstantResolver res)
            throws IOException {
        int flags = readMagnitude(in);

        if ((flags & 1) != 0) {
            cond = readExprAST(in, res);
        }
        if ((flags & 2) != 0) {
            interval = readExprAST(in, res);
        }
        if ((flags & 4) != 0) {
            message = readExprAST(in, res);
        }
    }

    @Override
    public void prepareWrite(ConstantResolver res) {
        prepareAST(cond, res);
        prepareAST(interval, res);
        prepareAST(message, res);
    }

    @Override
    protected void writeBody(DataOutput out, ConstantResolver res)
            throws IOException {
        int flags = (cond     == null ? 0 : 1)
                  | (interval == null ? 0 : 2)
                  | (message  == null ? 0 : 4);
        writePackedLong(out, flags);

        if (cond != null) {
            cond.writeExpr(out, res);
        }
        if (interval != null) {
            interval.writeExpr(out, res);
        }
        if (message != null) {
            message.writeExpr(out, res);
        }
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append("assert");
        if (cond != null) {
            buf.append(' ')
               .append(cond);
        }
        if (message != null) {
            buf.append(" as ")
               .append(message);
        }
        buf.append(';');
        return buf.toString();
    }
}