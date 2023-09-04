package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static org.xvm.asm.ast.BinaryAST.NodeType.InvokeExpr;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Invocation expression for method or "constant" function calls.
 */
public class InvokeExprAST<C>
        extends CallableExprAST<C> {

    private C          method;
    private ExprAST<C> target;

    InvokeExprAST() {
    }

    /**
     * Construct an InvokeExprAST.
     */
    public InvokeExprAST(C method, C[] retTypes, ExprAST target, ExprAST<C>[] args) {
        super(retTypes, args);

        assert method != null && target != null;

        this.method = method;
        this.target = target;
    }

    @Override
    public NodeType nodeType() {
        return InvokeExpr;
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
        super.read(in, res);

        method = res.getConstant(readMagnitude(in));
        target = readExprAST(in, res);
    }

    @Override
    public void prepareWrite(ConstantResolver<C> res) {
        super.prepareWrite(res);

        method = res.register(method);
        target.prepareWrite(res);
    }

    @Override
    public void write(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        super.write(out, res);

        writePackedLong(out, res.indexOf(method));
        target.writeExpr(out, res);
    }

    @Override
    public String dump() {
        return target.dump() + '.' + method + "\n(" + super.dump() + ")\n";
    }

    @Override
    public String toString() {
        return target.toString() + '.' + method + '(' + super.toString() + ')';
    }
}