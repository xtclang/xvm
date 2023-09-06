package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Arrays;
import java.util.Objects;

import static org.xvm.asm.ast.BinaryAST.NodeType.Return0Stmt;
import static org.xvm.asm.ast.BinaryAST.NodeType.Return1Stmt;
import static org.xvm.asm.ast.BinaryAST.NodeType.ReturnNStmt;

import static org.xvm.util.Handy.indentLines;


/**
 * Zero or more nested statements.
 */
public class ReturnStmtAST<C>
        extends BinaryAST<C> {

    private NodeType     nodeType;
    private ExprAST<C>[] exprs;

    ReturnStmtAST(NodeType nodeType) {
        this.nodeType = nodeType;
    }

    public ReturnStmtAST(ExprAST<C>[] exprs) {
        if (exprs == null) {
            this.exprs    = NO_EXPRS;
            this.nodeType = Return0Stmt;
        } else {
            assert Arrays.stream(exprs).allMatch(Objects::nonNull);
            this.exprs    = exprs;
            this.nodeType = switch (exprs.length) {
                case 0  -> Return0Stmt;
                case 1  -> Return1Stmt;
                default -> ReturnNStmt;
            };
        }
    }

    @Override
    public NodeType nodeType() {
        return nodeType;
    }

    public ExprAST<C>[] getExprs() {
        return exprs; // note: caller must not modify returned array in any way
    }

    @Override
    public void read(DataInput in, ConstantResolver<C> res)
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
    public void prepareWrite(ConstantResolver<C> res) {
        prepareASTArray(exprs, res);
    }

    @Override
    public void write(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        out.writeByte(nodeType.ordinal());
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
    public String dump() {
        StringBuilder buf = new StringBuilder();
        buf.append(this);
        for (ExprAST child : exprs) {
            buf.append('\n').append(indentLines(child.dump(), "  "));
        }
        return buf.toString();
    }

    @Override
    public String toString() {
        return nodeType().name() + ":" + exprs.length;
    }
}