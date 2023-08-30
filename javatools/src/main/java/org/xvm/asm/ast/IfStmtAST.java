package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.util.Handy;

import static org.xvm.asm.ast.BinaryAST.NodeType.IfElseStmt;
import static org.xvm.asm.ast.BinaryAST.NodeType.IfThenStmt;


/**
 * Zero or more nested statements.
 */
public class IfStmtAST<C>
        extends BinaryAST<C> {

    private ExprAST<C>   cond;
    private BinaryAST<C> thenStmt;
    private boolean      noElse;
    private BinaryAST<C> elseStmt;

    IfStmtAST(boolean hasElse) {
        noElse = !hasElse;
    }

    public IfStmtAST(ExprAST<C> cond, BinaryAST<C> thenStmt) {
        assert cond != null && thenStmt != null;
        this.cond     = cond;
        this.thenStmt = thenStmt;
        this.noElse   = true;
    }

    public IfStmtAST(ExprAST<C> cond, BinaryAST<C> thenStmt, BinaryAST<C> elseStmt) {
        assert cond != null && thenStmt != null && elseStmt != null;
        this.cond     = cond;
        this.thenStmt = thenStmt;
        this.elseStmt = elseStmt;
    }

    @Override
    public NodeType nodeType() {
        return noElse ? IfThenStmt : IfElseStmt;
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
    public void read(DataInput in, ConstantResolver<C> res)
            throws IOException {
        res.enter();
        cond     = readExprAST(in, res);
        res.enter();
        thenStmt = readAST(in, res);
        res.exit();
        if (!noElse) {
            res.enter();
            elseStmt = readAST(in, res);
            res.exit();
        }
        res.exit();
    }

    @Override
    public void prepareWrite(ConstantResolver<C> res) {
        prepareAST(cond, res);
        prepareAST(thenStmt, res);
        prepareAST(elseStmt, res);
    }

    @Override
    public void write(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        out.writeByte(nodeType().ordinal());
        writeExprAST(cond, out, res);
        writeAST(thenStmt, out, res);
        if (!noElse) {
            writeAST(elseStmt, out, res);
        }
    }

    @Override
    public String dump() {
        StringBuilder buf = new StringBuilder();
        buf.append(this);
        buf.append('\n').append(Handy.indentLines(cond.dump(), "  "));
        buf.append("\nthen\n").append(Handy.indentLines(thenStmt.dump(), "  "));
        if (!noElse) {
            buf.append("\nelse\n").append(Handy.indentLines(elseStmt.dump(), "  "));
        }
        return buf.toString();
    }
}