package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.ast.LanguageAST.StmtAST;

import static org.xvm.asm.ast.LanguageAST.NodeType.EXPR_STMT;


/**
 * An expression whose return values are ignored, and which is treated as a statement.
 */
public class ExprStmtAST<C>
    extends StmtAST<C> {

    ExprStmtAST() {}

    public ExprStmtAST(ExprAST expr) {
        assert expr != null;
        this.expr = expr;
    }

    ExprAST<C> expr;

    @Override
    public NodeType nodeType() {
        return EXPR_STMT;
    }

    @Override
    public void read(DataInput in, ConstantResolver<C> res)
            throws IOException {
        expr = deserialize(in, res);
    }

    @Override
    public void prepareWrite(ConstantResolver<C> res) {
        expr.prepareWrite(res);
    }

    @Override
    public void write(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        out.writeByte(nodeType().ordinal());
        expr.write(out, res);
    }

    @Override
    public String dump() {
        return expr.dump();
    }

    @Override
    public String toString() {
        return expr.toString();
    }
}
