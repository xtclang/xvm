package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.util.Handy;

import static org.xvm.asm.ast.LanguageAST.NodeType.DO_WHILE_STMT;


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
        StringBuilder buf = new StringBuilder();
        buf.append(this);
        buf.append("\ndo\n").append(Handy.indentLines(body.dump(), "  "));
        buf.append("\nwhile\n").append(Handy.indentLines(cond.dump(), "  "));
        return buf.toString();
    }
}
