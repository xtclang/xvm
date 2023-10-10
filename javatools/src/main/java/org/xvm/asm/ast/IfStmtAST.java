package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static org.xvm.asm.ast.BinaryAST.NodeType.IfElseStmt;
import static org.xvm.asm.ast.BinaryAST.NodeType.IfThenStmt;

import static org.xvm.util.Handy.indentLines;


/**
 * Supports the "if..then" and "if..then..else" statements.
 */
public class IfStmtAST
        extends BinaryAST {

    private ExprAST   cond;
    private BinaryAST thenStmt;
    private boolean   hasElse;
    private BinaryAST elseStmt;

    IfStmtAST(NodeType nodeType) {
        this.hasElse = switch (nodeType) {
            case IfThenStmt -> false;
            case IfElseStmt -> true;
            default -> throw new IllegalArgumentException("nodeType=" + nodeType);
        };
    }

    public IfStmtAST(ExprAST cond, BinaryAST thenStmt, BinaryAST elseStmt) {
        assert cond != null;
        this.cond     = cond;
        this.thenStmt = unwrapStatement(thenStmt);
        this.elseStmt = unwrapStatement(elseStmt);
        this.hasElse  = elseStmt != null;
    }

    @Override
    public NodeType nodeType() {
        return hasElse ? IfElseStmt : IfThenStmt;
    }

    public ExprAST getCond() {
        return cond;
    }

    public BinaryAST getThen() {
        return thenStmt;
    }

    public BinaryAST getElse() {
        return elseStmt;
    }

    @Override
    protected void readBody(DataInput in, ConstantResolver res)
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
    public void prepareWrite(ConstantResolver res) {
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
    protected void writeBody(DataOutput out, ConstantResolver res)
            throws IOException {
        writeExprAST(cond, out, res);
        writeAST(thenStmt, out, res);
        if (hasElse) {
            writeAST(elseStmt, out, res);
        }
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append("if (")
           .append(cond)
           .append(") {\n")
           .append(indentLines(thenStmt.toString(), "  "))
           .append("\n}");

        if (hasElse) {
            buf.append(" else {\n")
               .append(indentLines(elseStmt.toString(), "  "))
               .append("\n}");
        }
        return buf.toString();
    }
}