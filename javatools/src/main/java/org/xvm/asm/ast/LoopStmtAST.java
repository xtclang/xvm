package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static org.xvm.asm.ast.BinaryAST.NodeType.LoopStmt;

import static org.xvm.util.Handy.indentLines;

/**
 * A "while(True){...}" statement.
 */
public class LoopStmtAST
        extends BinaryAST {

    private ExprAST[] specialRegs; // RegAllocAST[]
    private BinaryAST body;

    LoopStmtAST() {}

    public LoopStmtAST(RegAllocAST[] specialRegs, BinaryAST body) {
        assert body != null;

        this.specialRegs = specialRegs == null  ? NO_ALLOCS : specialRegs;
        this.body        = body;
    }

    @Override
    public NodeType nodeType() {
        return LoopStmt;
    }

    public BinaryAST getBody() {
        return body;
    }

    @Override
    protected void readBody(DataInput in, ConstantResolver res)
            throws IOException {
        res.enter();
        specialRegs = readExprArray(in, res);
        body        = readAST(in, res);
        res.exit();
    }

    @Override
    public void prepareWrite(ConstantResolver res) {
        res.enter();
        prepareASTArray(specialRegs, res);
        prepareAST(body, res);
        res.exit();
    }

    @Override
    protected void writeBody(DataOutput out, ConstantResolver res)
            throws IOException {
        writeExprArray(specialRegs, out, res);
        writeAST(body, out, res);
    }

    @Override
    public String toString() {
        return "while (True) {"
            + '\n' + indentLines(body.toString(), "  ")
            + "\n}";
    }
}