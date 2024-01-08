package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;

import org.xvm.asm.constants.FormalConstant;
import org.xvm.asm.constants.TypeConstant;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * A property deref expression.
 *
 * Note: consider creating a "local property deref" expression that operates on "this" and doesn't
 *       need the underlying expression.
 */
public class PropertyExprAST
        extends DelegatingExprAST {

    private Constant property;

    PropertyExprAST() {}

    public PropertyExprAST(ExprAST expr, Constant property) {
        super(expr);

        assert property != null;
        this.property = property;
    }

    public ExprAST getTarget() {
        return getExpr();
    }

    public Constant getProperty() {
        return property;
    }

    @Override
    public NodeType nodeType() {
        return NodeType.PropertyExpr;
    }

    @Override
    public TypeConstant getType(int i) {
        assert i == 0;

        TypeConstant typeProp = property.getType();
        if (typeProp.isFormalType()) {
            TypeConstant typeResolved = getExpr().getType(0).
                resolveFormalType((FormalConstant) typeProp.getDefiningConstant());
            if (typeResolved != null) {
                return typeResolved;
            }
        }
        return typeProp;
    }

    @Override
    public boolean isAssignable() {
        return true;
    }

    @Override
    protected void readBody(DataInput in, ConstantResolver res)
            throws IOException {
        super.readBody(in, res);

        property = res.getConstant(readMagnitude(in));
    }

    @Override
    public void prepareWrite(ConstantResolver res) {
        super.prepareWrite(res);

        property = res.register(property);
        }

    @Override
    protected void writeBody(DataOutput out, ConstantResolver res)
            throws IOException {
        super.writeBody(out, res);

        writePackedLong(out, res.indexOf(property));
    }

    @Override
    public String toString() {
        return getTarget().toString() + '.' + property.getValueString();
    }
}