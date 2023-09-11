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

    WhileStmtAST() {}

    public WhileStmtAST(ExprAST<C> cond, BinaryAST<C> body) {
        this.cond = cond;
        this.body = body;
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
        cond = readExprAST(in, res);
        res.enter();
        body = readAST(in, res);
        res.exit();
        res.exit();
    }

    @Override
    public void prepareWrite(ConstantResolver<C> res) {
        res.enter();
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