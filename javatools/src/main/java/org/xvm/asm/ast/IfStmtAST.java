package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.util.Handy;

import static org.xvm.asm.ast.BinaryAST.NodeType.IfElseStmt;
import static org.xvm.asm.ast.BinaryAST.NodeType.IfThenStmt;


/**
 * Supports the "if..then" and "if..then..else" statements.
 */
public class IfStmtAST<C>
        extends BinaryAST<C> {

    private ExprAST<C>   cond;
    private BinaryAST<C> thenStmt;
    private boolean      hasElse;
    private BinaryAST<C> elseStmt;

    IfStmtAST(boolean hasElse) {
        this.hasElse = hasElse;
    }

    public IfStmtAST(ExprAST<C> cond, BinaryAST<C> thenStmt) {
        assert cond != null && thenStmt != null;
        this.cond     = cond;
        this.thenStmt = thenStmt;
        this.hasElse  = false;
    }

    public IfStmtAST(ExprAST<C> cond, BinaryAST<C> thenStmt, BinaryAST<C> elseStmt) {
        assert cond != null && thenStmt != null && elseStmt != null;
        this.cond     = cond;
        this.thenStmt = thenStmt;
        this.elseStmt = elseStmt;
    }

    @Override
    public NodeType nodeType() {
        return hasElse ? IfElseStmt : IfThenStmt;
    }

    public ExprAST<C> getCond() {
        return cond;
    }

    public BinaryAST<C> getThen() {
        return thenStmt;
    }

    public BinaryAST<C> getElse() {
        return elseStmt;
    }

    @Override
    protected void readBody(DataInput in, ConstantResolver<C> res)
            throws IOException {
        res.enter();
        cond = readExprAST(in, res);
        res.enter();
        thenStmt = readAST(in, res);
        res.exit();
        if (hasElse) {
            res.enter();
            elseStmt = readAST(in, res);
            res.exit();
        }
        res.exit();
    }

    @Override
    public void prepareWrite(ConstantResolver<C> res) {
        res.enter();
        prepareAST(cond, res);
        res.enter();
        prepareAST(thenStmt, res);
        res.exit();
        if (hasElse) {
            res.enter();
            prepareAST(elseStmt, res);
            res.exit();
        }
        res.exit();
    }

    @Override
    protected void writeBody(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        writeExprAST(cond, out, res);
        writeAST(thenStmt, out, res);
        if (hasElse) {
            writeAST(elseStmt, out, res);
        }
    }

    @Override
    public String dump() {
        StringBuilder buf = new StringBuilder();
        buf.append(this);
        buf.append('\n').append(Handy.indentLines(cond.dump(), "  "));
        buf.append("\nthen\n").append(Handy.indentLines(thenStmt.dump(), "  "));
        if (hasElse) {
            buf.append("\nelse\n").append(Handy.indentLines(elseStmt.dump(), "  "));
        }
        return buf.toString();
    }
}