package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;

import static org.xvm.asm.ast.BinaryAST.NodeType.InitAst;


/**
 * A synthetic AST that represents a "default initialization" for the currently constructed class.
 */
public class InitAST
        extends BinaryAST {

    private InitAST() {
    }

    @Override
    public NodeType nodeType() {
        return InitAst;
    }

    @Override
    protected void readBody(DataInput in, ConstantResolver res) {
    }

    @Override
    public void prepareWrite(ConstantResolver res) {}

    @Override
    protected void writeBody(DataOutput out, ConstantResolver res) {
    }

    @Override
    public String toString() {
        return "<defaultInit>";
    }

    static public final InitAST INSTANCE = new InitAST();
}