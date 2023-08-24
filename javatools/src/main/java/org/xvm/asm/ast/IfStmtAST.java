package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.ast.LanguageAST.StmtAST;

import org.xvm.util.Handy;

import static org.xvm.asm.ast.LanguageAST.NodeType.IF_ELSE_STMT;
import static org.xvm.asm.ast.LanguageAST.NodeType.IF_THEN_STMT;


/**
 * Zero or more nested statements.
 */
public class IfStmtAST<C>
        extends StmtAST<C> {

    private ConditionAST<C> cond;
    private StmtAST<C>      thenStmt;
    private boolean         noElse;
    private StmtAST<C>      elseStmt;

    IfStmtAST(boolean hasElse) {
        noElse = !hasElse;
    }

    public IfStmtAST(ConditionAST<C> cond, StmtAST<C> thenStmt) {
        assert cond != null && thenStmt != null;
        this.cond     = cond;
        this.thenStmt = thenStmt;
        this.noElse   = true;
    }

    public IfStmtAST(ConditionAST<C> cond, StmtAST<C> thenStmt, StmtAST<C> elseStmt) {
        assert cond != null && thenStmt != null && elseStmt != null;
        this.cond     = cond;
        this.thenStmt = thenStmt;
        this.elseStmt = elseStmt;
    }

    @Override
    public NodeType nodeType() {
        return noElse ? IF_THEN_STMT : IF_ELSE_STMT;
    }

    public ConditionAST<C> getCond() {
        return cond;
    }

    public StmtAST<C> getThen() {
        return thenStmt;
    }

    public StmtAST<C> getElse() {
        return elseStmt;
    }

    @Override
    public void read(DataInput in, ConstantResolver<C> res)
            throws IOException {
        cond     = new ConditionAST<>(in, res);
        thenStmt = deserialize(in, res);
        if (!noElse) {
            elseStmt = deserialize(in, res);
        }
    }

    @Override
    public void prepareWrite(ConstantResolver<C> res) {
        cond.prepareWrite(res);
        thenStmt.prepareWrite(res);
        if (!noElse) {
            elseStmt.prepareWrite(res);
        }
    }

    @Override
    public void write(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        out.writeByte(nodeType().ordinal());

        cond.write(out, res);
        thenStmt.write(out, res);
        if (!noElse) {
            elseStmt.write(out, res);
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