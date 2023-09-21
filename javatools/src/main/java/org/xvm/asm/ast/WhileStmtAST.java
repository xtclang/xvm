package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static org.xvm.asm.ast.BinaryAST.NodeType.WhileDoStmt;

import static org.xvm.util.Handy.indentLines;


/**
 * A "while..do" statement.
 */
public class WhileStmtAST
        extends BinaryAST {

    private ExprAST   cond;
    private BinaryAST body;
    private ExprAST[] specialRegs;  // RegAllocAST[]
    private ExprAST[] declaredRegs; // RegAllocAST[]

    WhileStmtAST() {}

    public WhileStmtAST(RegAllocAST[] specialRegs, RegAllocAST[] declaredRegs,
                        ExprAST cond, BinaryAST body) {
        this.specialRegs  = specialRegs == null  ? NO_ALLOCS : specialRegs;
        this.declaredRegs = declaredRegs == null ? NO_ALLOCS : declaredRegs;
        this.cond         = cond;
        this.body         = body;
    }

    @Override
    public NodeType nodeType() {
        return WhileDoStmt;
    }

    public ExprAST getCond() {
        return cond;
    }

    public BinaryAST getBody() {
        return body;
    }

    @Override
    protected void readBody(DataInput in, ConstantResolver res)
            throws IOException {
        res.enter();
        specialRegs  = readExprArray(in, res);
        declaredRegs = readExprArray(in, res);
        cond         = readExprAST(in, res);
        res.enter();
        body = readAST(in, res);
        res.exit();
        res.exit();
    }

    @Override
    public void prepareWrite(ConstantResolver res) {
        res.enter();
        prepareASTArray(specialRegs, res);
        prepareASTArray(declaredRegs, res);
        prepareAST(cond, res);
        res.enter();
        prepareAST(body, res);
        res.exit();
        res.exit();
    }

    @Override
    protected void writeBody(DataOutput out, ConstantResolver res)
            throws IOException {
        writeExprArray(specialRegs, out, res);
        writeExprArray(declaredRegs, out, res);
        writeExprAST(cond, out, res);
        writeAST(body, out, res);
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append("while (")
           .append(cond)
           .append(") ");
        if (body == null) {
            buf.append("{}");
        } else {
            buf.append("{\n");
            buf.append(indentLines(body.toString(), "  "));
            buf.append("\n}");
        }
        return buf.toString();
    }
}