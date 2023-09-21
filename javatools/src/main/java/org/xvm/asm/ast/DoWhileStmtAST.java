package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static org.xvm.asm.ast.BinaryAST.NodeType.DoWhileStmt;

import static org.xvm.util.Handy.indentLines;


/**
 * A "do..while" statement.
 */
public class DoWhileStmtAST
        extends BinaryAST {

    private ExprAST[] specialRegs;  // RegAllocAST[]
    private BinaryAST body;
    private ExprAST   cond;

    DoWhileStmtAST() {}

    public DoWhileStmtAST(RegAllocAST[] specialRegs, BinaryAST body, ExprAST cond) {
        this.specialRegs = specialRegs == null  ? NO_ALLOCS : specialRegs;
        this.body = body;
        this.cond = cond;
    }

    @Override
    public NodeType nodeType() {
        return DoWhileStmt;
    }

    @Override
    protected void readBody(DataInput in, ConstantResolver res)
            throws IOException {
        res.enter();
        specialRegs = readExprArray(in, res);
        body        = readAST(in, res);
        cond        = readExprAST(in, res);
        res.exit();
    }

    @Override
    public void prepareWrite(ConstantResolver res) {
        res.enter();
        prepareASTArray(specialRegs, res);
        prepareAST(body, res);
        prepareAST(cond, res);
        res.exit();
    }

    @Override
    protected void writeBody(DataOutput out, ConstantResolver res)
            throws IOException {
        writeExprArray(specialRegs, out, res);
        writeAST(body, out, res);
        writeExprAST(cond, out, res);
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append("do {\n");
        if (body != null) {
            buf.append(indentLines(body.toString(), "  "));
        }
        buf.append("\n} while (")
           .append(cond)
           .append(");");
        return buf.toString();
    }
}