package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.util.Handy;

import static org.xvm.asm.ast.BinaryAST.NodeType.ForStmt;


/**
 * A "for(init;cond;update){...}" statement.
 */
public class ForStmtAST<C>
        extends BinaryAST<C> {

    private BinaryAST<C> init;
    private ExprAST<C>   cond;
    private BinaryAST<C> update;
    private BinaryAST<C> body;

    ForStmtAST() {}

    public ForStmtAST(BinaryAST<C> init, ExprAST<C> cond, BinaryAST<C> update, BinaryAST<C> body) {
        this.init   = init;
        this.cond   = cond;
        this.update = update;
        this.body   = body;
    }

    @Override
    public NodeType nodeType() {
        return ForStmt;
    }

    public BinaryAST<C> getInit() {
        return init;
    }

    public ExprAST<C> getCond() {
        return cond;
    }

    public BinaryAST<C> getUpdate() {
        return update;
    }

    public BinaryAST<C> getBody() {
        return body;
    }

    @Override
    public void read(DataInput in, ConstantResolver<C> res)
            throws IOException {
        init   = readAST(in, res);
        cond   = readExprAST(in, res);
        update = readAST(in, res);
        body   = readAST(in, res);
    }

    @Override
    public void prepareWrite(ConstantResolver<C> res) {
        prepareAST(init, res);
        prepareAST(cond, res);
        prepareAST(update, res);
        prepareAST(body, res);
    }

    @Override
    public void write(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        out.writeByte(nodeType().ordinal());
        writeAST(init, out, res);
        writeExprAST(cond, out, res);
        writeAST(update, out, res);
        writeAST(body, out, res);
    }

    @Override
    public String dump() {
        StringBuilder buf = new StringBuilder("for (");
        if (init != null) {
            buf.append(init.dump());
        }
        buf.append("; ");
        if (cond != null) {
            buf.append(cond.dump());
        }
        buf.append("; ");
        if (update != null) {
            buf.append(update.dump());
        }
        buf.append(") ");
        if (body == null) {
            buf.append("{}");
        } else {
            buf.append('\n')
               .append(Handy.indentLines(body.dump(), "  "));
        }
        return buf.toString();
    }

    @Override
    public String toString() {
        return "for (,,) {}";
    }
}