package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.ast.LanguageAST.StmtAST;

import static org.xvm.asm.ast.LanguageAST.NodeType.WHILE_DO_STMT;

import static org.xvm.util.Handy.indentLines;


/**
 * A "while..do" statement.
 */
public class WhileStmtAST<C>
        extends StmtAST<C> {

    private ConditionAST<C> cond;
    private StmtAST<C>      body;

    WhileStmtAST() {}

    public WhileStmtAST(ConditionAST<C> cond, StmtAST<C> body) {
        assert cond != null && body != null;
        this.cond = cond;
        this.body = body;
    }

    @Override
    public NodeType nodeType() {
        return WHILE_DO_STMT;
    }

    public ConditionAST<C> getCond() {
        return cond;
    }

    public StmtAST<C> getBody() {
        return body;
    }

    @Override
    public void read(DataInput in, ConstantResolver<C> res)
            throws IOException {
        cond = new ConditionAST<>(in, res);
        body = deserialize(in, res);
    }

    @Override
    public void prepareWrite(ConstantResolver<C> res) {
        cond.prepareWrite(res);
        body.prepareWrite(res);
    }

    @Override
    public void write(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        out.writeByte(nodeType().ordinal());

        cond.write(out, res);
        body.write(out, res);
    }

    @Override
    public String dump() {
        return "while\n" + indentLines(cond.dump(), "  ") +
               "\ndo\n"  + indentLines(body.dump(), "  ");
    }

    @Override
    public String toString() {
        return "while (" + cond.toString() + ") {}";
    }
}