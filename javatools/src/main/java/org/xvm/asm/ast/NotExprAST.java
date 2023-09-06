package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.IOException;


/**
 * A Boolean "not" (!) expression.
 */
public class NotExprAST<C>
        extends DelegatingExprAST<C>
    {
    private transient C booleanType;

    NotExprAST() {}

    public NotExprAST(ExprAST<C> expr) {
        super(expr);
    }

    @Override
    public C getType(int i) {
        assert i == 0;
        return booleanType;
    }

    @Override
    public NodeType nodeType() {
        return NodeType.NotExpr;
    }

    @Override
    protected void readBody(DataInput in, ConstantResolver<C> res)
            throws IOException {
        super.readBody(in, res);

        booleanType = res.typeForName("Boolean");
    }
}