package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * An expressions that follow the pattern "operator expression".
 */
public class UnaryOpExprAST<C>
        extends PrefixExprAST<C> {

    private C type;

    UnaryOpExprAST() {}

    public UnaryOpExprAST(C type, Operator op, ExprAST<C> expr) {
        super(op, expr);

        assert type != null;
        assert op != null && op != Operator.Not;

        this.type = type;
    }

    @Override
    public C getType(int i) {
        assert i == 0;
        return type;
    }

    @Override
    public NodeType nodeType() {
        return NodeType.UnaryOpExpr;
    }

    @Override
    public void read(DataInput in, ConstantResolver<C> res)
            throws IOException {
        super.read(in, res);

        type = res.getConstant(readMagnitude(in));
    }

    @Override
    public void prepareWrite(ConstantResolver<C> res) {
        super.prepareWrite(res);

        type = res.register(type);
    }

    @Override
    public void write(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        super.write(out, res);

        writePackedLong(out, res.indexOf(type));
    }

    @Override
    public String dump() {
        return type + ": " + super.dump();
    }
}