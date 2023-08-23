package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.ast.LanguageAST.StmtAST;

import org.xvm.util.Handy;

import static org.xvm.asm.ast.LanguageAST.NodeType.LOOP_STMT;

/**
 * A "while(True){...}" statement.
 */
public class LoopStmtAST<C>
        extends StmtAST<C> {

    protected StmtAST<C> body;

    LoopStmtAST() {}

    public LoopStmtAST(StmtAST<C> body) {
        assert body != null;
        this.body = body;
    }

    @Override
    public NodeType nodeType() {
        return LOOP_STMT;
    }

    public StmtAST<C> getBody() {
        return body;
    }

    @Override
    public void read(DataInput in, ConstantResolver<C> res)
            throws IOException {
        body = deserialize(in, res);
    }

    @Override
    public void prepareWrite(ConstantResolver<C> res) {
        body.prepareWrite(res);
    }

    @Override
    public void write(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        out.writeByte(nodeType().ordinal());
        body.write(out, res);
    }

    @Override
    public String dump() {
        return this + "\n" + Handy.indentLines(body.dump(), "  ");
    }

    @Override
    public String toString() {
        return "while(True){}";
    }
}