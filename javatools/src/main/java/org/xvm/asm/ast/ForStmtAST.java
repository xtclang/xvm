package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.util.Handy;

import static org.xvm.asm.ast.BinaryAST.NodeType.ForStmt;


/**
 * A "for(init;cond;update){...}" statement.
 */
public class ForStmtAST
        extends BinaryAST {

    private ExprAST[] specialRegs; // RegAllocAST[]
    private BinaryAST init;
    private ExprAST   cond;
    private BinaryAST update;
    private BinaryAST body;

    ForStmtAST() {}

    public ForStmtAST(RegAllocAST[] specialRegs, BinaryAST init, ExprAST cond,
                      BinaryAST update, BinaryAST body) {
        this.specialRegs = specialRegs == null  ? NO_ALLOCS : specialRegs;
        this.init        = init;
        this.cond        = cond;
        this.update      = update;
        this.body        = body;
    }

    @Override
    public NodeType nodeType() {
        return ForStmt;
    }

    public BinaryAST getInit() {
        return init;
    }

    public ExprAST getCond() {
        return cond;
    }

    public BinaryAST getUpdate() {
        return update;
    }

    public BinaryAST getBody() {
        return body;
    }

    @Override
    protected void readBody(DataInput in, ConstantResolver res)
            throws IOException {
        res.enter();
        specialRegs = readExprArray(in, res);
        init        = readAST(in, res);
        cond        = readExprAST(in, res);
        update      = readAST(in, res);
        res.enter();
        body = readAST(in, res);
        res.exit();
        res.exit();
    }

    @Override
    public void prepareWrite(ConstantResolver res) {
        res.enter();
        prepareASTArray(specialRegs, res);
        prepareAST(init, res);
        prepareAST(cond, res);
        prepareAST(update, res);
        res.enter();
        prepareAST(body, res);
        res.exit();
        res.exit();
    }

    @Override
    protected void writeBody(DataOutput out, ConstantResolver res)
            throws IOException {
        writeExprArray(specialRegs, out, res);
        writeAST(init, out, res);
        writeExprAST(cond, out, res);
        writeAST(update, out, res);
        writeAST(body, out, res);
    }

    @Override
    public String toString() {
        var sb = new StringBuilder("for (");
        if (init != null) {
            sb.append(init);
        }
        sb.append("; ");
        if (cond != null) {
            sb.append(cond);
        }
        sb.append("; ");
        if (update != null) {
            sb.append(update);
        }
        sb.append(") ");
        if (body == null) {
            sb.append("{}");
        } else {
            sb.append("{\n")
               .append(Handy.indentLines(body.toString(), "  "))
               .append("\n}");
        }
        return sb.toString();
    }
}