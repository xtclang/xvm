package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static org.xvm.asm.ast.LanguageAST.NodeType.DO_WHILE_STMT;

import static org.xvm.util.Handy.indentLines;


/**
 * A "do..while" statement.
 */
public class DoWhileStmtAST<C>
        extends WhileStmtAST<C> {

    DoWhileStmtAST() {}

    public DoWhileStmtAST(StmtAST<C> body, ConditionAST<C> cond) {
        super(cond, body);
    }

    @Override
    public NodeType nodeType() {
        return DO_WHILE_STMT;
    }

    @Override
    public void read(DataInput in, ConstantResolver<C> res)
            throws IOException {
        body = deserialize(in, res);
        cond = new ConditionAST<C>();
        cond.read(in, res);
    }

    @Override
    public void prepareWrite(ConstantResolver<C> res) {
        body.prepareWrite(res);
        cond.prepareWrite(res);
    }

    @Override
    public void write(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        out.writeByte(nodeType().ordinal());
        body.write(out, res);
        cond.write(out, res);
    }

    @Override
    public String dump() {
        return "do\n" + indentLines(body.dump(), "  ") +
               "\nwhile\n" + indentLines(cond.dump(), "  ");
    }

    @Override
    public String toString() {
        return "do {} while(" + cond.toString() + ")";
    }
}