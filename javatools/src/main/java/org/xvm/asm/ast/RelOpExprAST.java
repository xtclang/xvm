package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.constants.TypeConstant;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * An expressions that follow the pattern "expression operator expression" and could be of any type.
 */
public class RelOpExprAST
        extends BiExprAST {

    private TypeConstant type;

    RelOpExprAST() {}

    public RelOpExprAST(ExprAST expr1, Operator op, ExprAST expr2, TypeConstant type) {
        super(expr1, op, expr2);

        assert type != null;
        this.type = type;
    }

    @Override
    public NodeType nodeType() {
        return NodeType.RelOpExpr;
    }

    @Override
    public TypeConstant getType(int i) {
        assert i == 0;
        return type;
    }

    @Override
    protected void readBody(DataInput in, ConstantResolver res)
            throws IOException {
        super.readBody(in, res);

        type = (TypeConstant) res.getConstant(readMagnitude(in));
    }

    @Override
    public void prepareWrite(ConstantResolver res) {
        super.prepareWrite(res);

        type = (TypeConstant) res.register(type);
    }

    @Override
    protected void writeBody(DataOutput out, ConstantResolver res)
            throws IOException {
        super.writeBody(out, res);

        writePackedLong(out, res.indexOf(type));
    }
}