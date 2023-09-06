package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * A property deref expression.
 *
 * Note: consider creating a "local property deref" expression that operates on "this" and doesn't
 *       need the underlying expression.
 */
public class PropertyExprAST<C>
        extends DelegatingExprAST<C> {

    private           C property;
    private transient C type;

    PropertyExprAST() {}

    public PropertyExprAST(ExprAST<C> expr, C property) {
        super(expr);

        assert property != null;
        this.property = property;
    }

    public ExprAST<C> getTarget() {
        return getExpr();
    }

    public C getProperty() {
        return property;
    }

    @Override
    public C getType(int i) {
        assert i == 0;
        return type;
    }

    @Override
    public NodeType nodeType() {
        return NodeType.PropertyExpr;
    }

    @Override
    protected void readBody(DataInput in, ConstantResolver<C> res)
            throws IOException {
        super.readBody(in, res);

        property = res.getConstant(readMagnitude(in));
        type     = res.typeOf(property);
    }

    @Override
    protected void writeBody(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        super.writeBody(out, res);

        writePackedLong(out, res.indexOf(property));
    }

    @Override
    public String dump() {
        return getTarget().dump() + '.' + property;
    }

    @Override
    public String toString() {
        return getTarget().toString() + '.' + property;
    }
}