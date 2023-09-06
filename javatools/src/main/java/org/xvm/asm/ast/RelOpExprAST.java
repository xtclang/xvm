package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * An expressions that follow the pattern "expression operator expression" and could be of any type.
 */
public class RelOpExprAST<C>
        extends BiExprAST<C> {

    private C type;

    RelOpExprAST() {}

    public RelOpExprAST(ExprAST<C> expr1, Operator op, ExprAST<C> expr2, C type) {
        super(expr1, op, expr2);

        assert type != null;
        this.type = type;
    }

    @Override
    public C getType(int i) {
        assert i == 0;
        return type;
    }

    @Override
    public NodeType nodeType() {
        return NodeType.RelOpExpr;
    }

    @Override
    protected void readBody(DataInput in, ConstantResolver<C> res)
            throws IOException {
        super.readBody(in, res);

        type = res.getConstant(readMagnitude(in));
    }

    @Override
    public void prepareWrite(ConstantResolver<C> res) {
        super.prepareWrite(res);

        type = res.register(type);
    }

    @Override
    protected void writeBody(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        super.writeBody(out, res);

        writePackedLong(out, res.indexOf(type));
    }

    @Override
    public String dump() {
        return type + ": " + super.dump();
    }
}