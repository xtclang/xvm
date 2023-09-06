package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static org.xvm.asm.ast.BinaryAST.NodeType.DoWhileStmt;

import static org.xvm.util.Handy.indentLines;


/**
 * A "do..while" statement.
 */
public class DoWhileStmtAST<C>
        extends BinaryAST<C> {

    private BinaryAST<C> body;
    private ExprAST<C>   cond;

    DoWhileStmtAST() {}

    public DoWhileStmtAST(BinaryAST<C> body, ExprAST<C> cond) {
        this.body = body;
        this.cond = cond;
    }

    @Override
    public NodeType nodeType() {
        return DoWhileStmt;
    }

    @Override
    public void read(DataInput in, ConstantResolver<C> res)
            throws IOException {
        res.enter();
        body = readAST(in, res);
        cond = readExprAST(in, res);
        res.exit();
    }

    @Override
    public void prepareWrite(ConstantResolver<C> res) {
        res.enter();
        prepareAST(body, res);
        prepareAST(cond, res);
        res.exit();
    }

    @Override
    public void write(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        out.writeByte(nodeType().ordinal());
        writeAST(body, out, res);
        writeExprAST(cond, out, res);
    }

    @Override
    public String dump() {
        return new StringBuffer()
            .append("do\n")
            .append(body == null ? "  {}" : indentLines(body.dump(), "  "))
            .append("\nwhile (")
            .append(cond == null ? "" : cond.dump())
            .append(")")
            .toString();
    }

    @Override
    public String toString() {
        return "do {} while(" + cond.toString() + ")";
    }
}