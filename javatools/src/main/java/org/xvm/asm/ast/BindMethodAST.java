package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static org.xvm.asm.ast.BinaryAST.ExprAST;
import static org.xvm.asm.ast.BinaryAST.NodeType.BindMethodExpr;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Bind method's target.
 */
public class BindMethodAST<C>
        extends ExprAST<C>
    {
    private ExprAST<C> target;
    private C          method;
    private C          type;

    BindMethodAST() {
    }

    /**
     * Construct an BindMethodAST.
     *
     * @param type  the type of the resulting (bound) function
     */
    public BindMethodAST(ExprAST<C> target, C method, C type) {
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
    public C getType(int i) {
        assert i == 0;
        return type;
    }

    public ExprAST<C> getTarget() {
        return target;
    }

    public C getMethod() {
        return method;
    }

    @Override
    public void read(DataInput in, ConstantResolver<C> res)
            throws IOException {
        target = readExprAST(in, res);
        method = res.getConstant(readMagnitude(in));
        type   = res.getConstant(readMagnitude(in));
    }

    @Override
    public void prepareWrite(ConstantResolver<C> res) {
        target.prepareWrite(res);
        method = res.register(method);
        type   = res.register(type);
    }

    @Override
    public void write(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        target.writeExpr(out, res);
        writePackedLong(out, res.indexOf(method));
        writePackedLong(out, res.indexOf(type));
    }

    @Override
    public String dump() {
        return target.dump() + '&' + method;
    }

    @Override
    public String toString() {
        return target.toString() + '&' + method;
    }
}