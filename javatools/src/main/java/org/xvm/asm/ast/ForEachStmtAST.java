package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.util.Handy;


/**
 * A "for(lval : expr){...}" statement.
 */
public class ForEachStmtAST
        extends BinaryAST {

    private final NodeType nodeType;
    private ExprAST[]   specialRegs; // RegAllocAST[]
    private ExprAST     lval;
    private ExprAST     rval;
    private BinaryAST   body;

    ForEachStmtAST(NodeType nodeType) {
        assert nodeTypeOk(nodeType);
        this.nodeType = nodeType;
    }

    public ForEachStmtAST(NodeType         nodeType,
                          RegAllocAST[] specialRegs,
                          ExprAST       lval,
                          ExprAST       rval,
                          BinaryAST     body) {
        assert nodeTypeOk(nodeType) && lval != null && rval != null;

        this.nodeType    = nodeType;
        this.specialRegs = specialRegs == null  ? NO_ALLOCS : specialRegs;
        this.lval        = lval;
        this.rval        = rval;
        this.body        = body;
    }

    static private boolean nodeTypeOk(NodeType nodeType) {
        return switch (nodeType) {
            default -> false;
            case ForIteratorStmt,
                 ForRangeStmt,
                 ForListStmt,
                 ForMapStmt,
                 ForIterableStmt -> true;
        };
    }

    @Override
    public NodeType nodeType() {
        return nodeType;
    }

    public ExprAST getLValue() {
        return lval;
    }

    public ExprAST getRValue() {
        return rval;
    }

    public BinaryAST getBody() {
        return body;
    }

    @Override
    protected void readBody(DataInput in, ConstantResolver res)
            throws IOException {
        res.enter();
        specialRegs = readExprArray(in, res);
        lval        = readExprAST(in, res);
        rval        = readExprAST(in, res);
        res.enter();
        body = readAST(in, res);
        res.exit();
        res.exit();
    }

    @Override
    public void prepareWrite(ConstantResolver res) {
        res.enter();
        prepareASTArray(specialRegs, res);
        prepareAST(lval, res);
        prepareAST(rval, res);
        res.enter();
        prepareAST(body, res);
        res.exit();
        res.exit();
    }

    @Override
    protected void writeBody(DataOutput out, ConstantResolver res)
            throws IOException {
        writeExprArray(specialRegs, out, res);
        writeExprAST(lval, out, res);
        writeExprAST(rval, out, res);
        writeAST(body, out, res);
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder("for (");
        buf.append(lval);
        buf.append(": ");
        buf.append(rval);
        buf.append(") ");
        if (body == null) {
            buf.append("{}");
        } else {
            buf.append("{\n")
               .append(Handy.indentLines(body.toString(), "  "))
               .append("\n}");
        }
        return buf.toString();
    }
}