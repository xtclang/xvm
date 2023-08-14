package org.xvm.asm.node;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.node.LanguageNode.StatementNode;


/**
 * An expression whose return values are ignored, and which is treated as a statement.
 */
public class ExpressionStatement<C>
        extends StatementNode<C> {

    ExpressionStatement() {}

    public ExpressionStatement(ExpressionNode expr) {
        assert expr != null;
        this.expr = expr;
    }

    ExpressionNode<C> expr;

    @Override
    public int nodeType() {
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
        out.writeByte(nodeType());
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
