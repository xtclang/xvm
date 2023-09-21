package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;

import org.xvm.asm.constants.TypeConstant;

import static org.xvm.asm.ast.BinaryAST.NodeType.BindMethodExpr;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Bind method's target.
 */
public class BindMethodAST
        extends ExprAST
    {
    private ExprAST      target;
    private Constant     method;
    private TypeConstant type;

    BindMethodAST() {
    }

    /**
     * Construct an BindMethodAST.
     *
     * @param type  the type of the resulting (bound) function
     */
    public BindMethodAST(ExprAST target, Constant method, TypeConstant type) {
        assert target != null && method != null && type != null;

        this.target = target;
        this.method = method;
        this.type   = type;
    }

    @Override
    public NodeType nodeType() {
        return BindMethodExpr;
    }

    @Override
    public TypeConstant getType(int i) {
        assert i == 0;
        return type;
    }

    public ExprAST getTarget() {
        return target;
    }

    public Constant getMethod() {
        return method;
    }

    @Override
    protected void readBody(DataInput in, ConstantResolver res)
            throws IOException {
        target = readExprAST(in, res);
        method = res.getConstant(readMagnitude(in));
        type   = (TypeConstant) res.getConstant(readMagnitude(in));
    }

    @Override
    public void prepareWrite(ConstantResolver res) {
        target.prepareWrite(res);
        method = res.register(method);
        type   = (TypeConstant) res.register(type);
    }

    @Override
    protected void writeBody(DataOutput out, ConstantResolver res)
            throws IOException {
        target.writeExpr(out, res);
        writePackedLong(out, res.indexOf(method));
        writePackedLong(out, res.indexOf(type));
    }

    @Override
    public String toString() {
        return target.toString() + '&' + method;
    }
}