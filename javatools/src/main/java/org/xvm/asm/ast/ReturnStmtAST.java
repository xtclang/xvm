package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Arrays;
import java.util.Objects;

import static org.xvm.asm.ast.BinaryAST.NodeType.Return0Stmt;
import static org.xvm.asm.ast.BinaryAST.NodeType.Return1Stmt;
import static org.xvm.asm.ast.BinaryAST.NodeType.ReturnNStmt;


/**
 * Zero or more nested statements.
 */
public class ReturnStmtAST
        extends BinaryAST {

    private final NodeType nodeType;
    private ExprAST[] exprs;

    ReturnStmtAST(NodeType nodeType) {
        this.nodeType = nodeType;
    }

    public ReturnStmtAST(ExprAST expr) {
        this(new ExprAST[] {expr});
    }
    public ReturnStmtAST(ExprAST[] exprs) {
        assert Arrays.stream(exprs).allMatch(Objects::nonNull);
        this.exprs    = exprs;
        this.nodeType = switch (exprs.length) {
            case 0  -> Return0Stmt;
            case 1  -> Return1Stmt;
            default -> ReturnNStmt;
        };
    }

    @Override
    public NodeType nodeType() {
        return nodeType;
    }

    public ExprAST[] getExprs() {
        return exprs; // note: caller must not modify returned array in any way
    }

    @Override
    protected void readBody(DataInput in, ConstantResolver res)
            throws IOException {
        switch (nodeType) {
        case Return0Stmt:
            exprs = NO_EXPRS;
            break;
        case Return1Stmt:
            ExprAST expr = readExprAST(in, res);
            assert expr != null;
            exprs = new ExprAST[] {expr};
            break;
        case ReturnNStmt:
            exprs = readExprArray(in, res);
            break;
        default:
            throw new IllegalStateException("nodeType=" + nodeType);
        }
    }

    @Override
    public void prepareWrite(ConstantResolver res) {
        prepareASTArray(exprs, res);
    }

    @Override
    protected void writeBody(DataOutput out, ConstantResolver res)
            throws IOException {
        switch (nodeType) {
        case Return0Stmt:
            break;
        case Return1Stmt:
            exprs[0].writeExpr(out, res);
            break;
        case ReturnNStmt:
            writeExprArray(exprs, out, res);
            break;
        default:
            throw new IllegalStateException("nodeType=" + nodeType);
        }
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder("return");
        for (int i = 0, c = exprs.length; i < c; ++i) {
            buf.append(i == 0 ? " " : ", ")
               .append(exprs[i].toString());
        }
        return buf.append(";").toString();
    }
}