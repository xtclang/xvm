package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.IOException;


/**
 * A Boolean "not" (!) expression.
 */
public class NotExprAST<C>
        extends PrefixExprAST<C> {

    private transient C booleanType;

    NotExprAST() {}

    public NotExprAST(ExprAST<C> expr) {
        super(Operator.Not, expr);
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
    public void read(DataInput in, ConstantResolver<C> res)
            throws IOException {
        super.read(in, res);

        booleanType = res.typeForName("Boolean");
    }
}