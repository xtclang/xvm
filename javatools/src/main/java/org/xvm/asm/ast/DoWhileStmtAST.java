package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static org.xvm.asm.ast.BinaryAST.NodeType.DoWhileStmt;

import static org.xvm.util.Handy.indentLines;


/**
 * A "do..while" statement.
 */
public class DoWhileStmtAST<C>
        extends BinaryAST<C> {

    private ExprAST<C>[] specialRegs;  // RegAllocAST<C>[]
    private BinaryAST<C> body;
    private ExprAST<C>   cond;

    DoWhileStmtAST() {}

    public DoWhileStmtAST(RegAllocAST<C>[] specialRegs, BinaryAST<C> body, ExprAST<C> cond) {
        this.specialRegs = specialRegs == null  ? NO_ALLOCS : specialRegs;
        this.body = body;
        this.cond = cond;
    }

    @Override
    public NodeType nodeType() {
        return DoWhileStmt;
    }

    @Override
    protected void readBody(DataInput in, ConstantResolver<C> res)
            throws IOException {
        res.enter();
        specialRegs = readExprArray(in, res);
        body        = readAST(in, res);
        cond        = readExprAST(in, res);
        res.exit();
    }

    @Override
    public void prepareWrite(ConstantResolver<C> res) {
        res.enter();
        prepareASTArray(specialRegs, res);
        prepareAST(body, res);
        prepareAST(cond, res);
        res.exit();
    }

    @Override
    protected void writeBody(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        writeExprArray(specialRegs, out, res);
        writeAST(body, out, res);
        writeExprAST(cond, out, res);
    }

    @Override
    public String dump() {
        StringBuilder buf = new StringBuilder();
        buf.append("do\n");
        if (body == null) {
            buf.append("  {}");
        } else {
            buf.append(indentLines(body.dump(), "  "));
        }
        buf.append("\nwhile (");
        if (cond == null) {
            buf.append(cond.dump());
        }
        buf.append(")");
        return buf.toString();
    }

    @Override
    public String toString() {
        return "do {} while(" + cond.toString() + ")";
    }
}