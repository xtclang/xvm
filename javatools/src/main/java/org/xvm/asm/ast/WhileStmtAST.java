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
    public void read(DataInput in, ConstantResolver<C> res)
            throws IOException {
        cond = readExprAST(in, res);
        body = readAST(in, res);
    }

    @Override
    public void prepareWrite(ConstantResolver<C> res) {
        prepareAST(cond, res);
        prepareAST(body, res);
    }

    @Override
    public void write(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        out.writeByte(nodeType().ordinal());
        writeExprAST(cond, out, res);
        writeAST(body, out, res);
    }

    @Override
    public String dump() {
        return new StringBuffer()
            .append("\nwhile (")
            .append(cond == null ? "" : cond.dump())
            .append(")\n")
            .append(body == null ? "  {}" : indentLines(body.dump(), "  "))
            .toString();
    }

    @Override
    public String toString() {
        return "while (" + cond.toString() + ") {}";
    }
}