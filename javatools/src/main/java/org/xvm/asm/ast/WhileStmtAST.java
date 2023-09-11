package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static org.xvm.asm.ast.BinaryAST.NodeType.WhileDoStmt;

import static org.xvm.util.Handy.indentLines;


/**
 * A "while..do" statement.
 */
public class WhileStmtAST<C>
        extends BinaryAST<C> {

    private ExprAST<C>   cond;
    private BinaryAST<C> body;
    private ExprAST<C>[] specialRegs;  // RegAllocAST<C>[]
    private ExprAST<C>[] declaredRegs; // RegAllocAST<C>[]

    WhileStmtAST() {}

    public WhileStmtAST(RegAllocAST<C>[] specialRegs, RegAllocAST<C>[] declaredRegs,
                        ExprAST<C> cond, BinaryAST<C> body) {
        this.specialRegs  = specialRegs == null  ? NO_ALLOCS : specialRegs;
        this.declaredRegs = declaredRegs == null ? NO_ALLOCS : declaredRegs;
        this.cond         = cond;
        this.body         = body;
    }

    @Override
    public NodeType nodeType() {
        return WhileDoStmt;
    }

    public ExprAST<C> getCond() {
        return cond;
    }

    public BinaryAST<C> getBody() {
        return body;
    }

    @Override
    protected void readBody(DataInput in, ConstantResolver<C> res)
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
    public void prepareWrite(ConstantResolver<C> res) {
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
    protected void writeBody(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        writeExprAST(cond, out, res);
        writeExprArray(specialRegs, out, res);
        writeExprArray(declaredRegs, out, res);
        writeAST(body, out, res);
    }

    @Override
    public String dump() {
        StringBuilder buf = new StringBuilder();
        buf.append("\nwhile (");
        if (cond != null) {
            buf.append(cond.dump());
        }
        buf.append(")\n");
        if (body == null) {
            buf.append("  {}");
        } else {
            buf.append(indentLines(body.dump(), "  "));
        }
        return buf.toString();
    }

    @Override
    public String toString() {
        return "while (" + cond.toString() + ") {}";
    }
}