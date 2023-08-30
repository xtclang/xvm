package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static org.xvm.asm.ast.BinaryAST.NodeType.LoopStmt;

import static org.xvm.util.Handy.indentLines;

/**
 * A "while(True){...}" statement.
 */
public class LoopStmtAST<C>
        extends BinaryAST<C> {

    private BinaryAST<C> body;

    LoopStmtAST() {}

    public LoopStmtAST(BinaryAST<C> body) {
        assert body != null;
        this.body = body;
    }

    @Override
    public NodeType nodeType() {
        return LoopStmt;
    }

    public BinaryAST<C> getBody() {
        return body;
    }

    @Override
    public void read(DataInput in, ConstantResolver<C> res)
            throws IOException {
        body = readAST(in, res);
    }

    @Override
    public void prepareWrite(ConstantResolver<C> res) {
        prepareAST(body, res);
    }

    @Override
    public void write(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        out.writeByte(nodeType().ordinal());
        writeAST(body, out, res);
    }

    @Override
    public String dump() {
        return this + "\n" + indentLines(body.dump(), "  ");
    }

    @Override
    public String toString() {
        return "while(True){}";
    }
}