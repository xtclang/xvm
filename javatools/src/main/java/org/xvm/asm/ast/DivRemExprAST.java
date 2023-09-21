package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.constants.TypeConstant;


/**
 * A DivRem (/%) expression. It differs from {@link RelOpExprAST} that it has two return types.
 */
public class DivRemExprAST
        extends BiExprAST {

    private TypeConstant[] types;

    DivRemExprAST() {}

    public DivRemExprAST(TypeConstant[] types, ExprAST expr1, ExprAST expr2) {
        super(expr1, Operator.DivRem, expr2);

        assert types != null && types.length == 2;
        this.types = types;
    }

    @Override
    public NodeType nodeType() {
        return NodeType.DivRemExpr;
    }

    @Override
    public int getCount() {
        return 2;
    }

    @Override
    public TypeConstant getType(int i) {
        return types[i];
    }

    @Override
    protected void readBody(DataInput in, ConstantResolver res)
            throws IOException {
        super.readBody(in, res);

        types = readTypeArray(in, res);
    }

    @Override
    public void prepareWrite(ConstantResolver res) {
        super.prepareWrite(res);
        prepareConstArray(types, res);
    }

    @Override
    protected void writeBody(DataOutput out, ConstantResolver res)
            throws IOException {
        super.writeBody(out, res);

        writeConstArray(types, out, res);
    }
}