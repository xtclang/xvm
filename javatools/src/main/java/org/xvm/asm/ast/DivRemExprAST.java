package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;


/**
 * A DivRem (/%) expression. It differs from {@link RelOpExprAST} that it has two return types.
 */
public class DivRemExprAST<C>
        extends BiExprAST<C> {

    private Object[] types;

    DivRemExprAST() {}

    public DivRemExprAST(Object[] types, ExprAST<C> expr1, ExprAST<C> expr2) {
        super(Operator.DivRem, expr1, expr2);

        assert types != null && types.length == 2;
        this.types = types;
    }

    @Override
    public int getCount() {
        return 2;
    }

    @Override
    public C getType(int i) {
        return (C) types[i];
    }

    @Override
    public NodeType nodeType() {
        return NodeType.DivRemExpr;
    }

    @Override
    public void read(DataInput in, ConstantResolver<C> res)
            throws IOException {
        super.read(in, res);

        types = readConstArray(in, res);
    }

    @Override
    public void prepareWrite(ConstantResolver<C> res) {
        super.prepareWrite(res);

        res.registerAll(types);
    }

    @Override
    public void write(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        super.write(out, res);

        writeConstArray(types, out, res);
    }

    @Override
    public String dump() {
        return "(" + types[0] + ", " + types[1] + "): " + super.dump();
    }
}