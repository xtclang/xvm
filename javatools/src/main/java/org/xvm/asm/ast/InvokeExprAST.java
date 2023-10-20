package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.MethodStructure;

import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.TypeConstant;

import static org.xvm.asm.ast.BinaryAST.NodeType.InvokeAsyncExpr;
import static org.xvm.asm.ast.BinaryAST.NodeType.InvokeExpr;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Invocation expression for method or "constant" function calls.
 */
public class InvokeExprAST
        extends CallableExprAST {

    private final NodeType nodeType;
    private MethodConstant method;
    private ExprAST        target;

    InvokeExprAST(NodeType nodeType) {
        this.nodeType = nodeType;
    }

    /**
     * Construct an InvokeExprAST.
     */
    public InvokeExprAST(MethodConstant method, TypeConstant[] retTypes, ExprAST target,
                         ExprAST[] args, boolean async) {
        super(retTypes, args);

        assert method != null && target != null;

        this.nodeType = async ? InvokeAsyncExpr : InvokeExpr;
        this.method   = method;
        this.target   = target;
    }

    public ExprAST getTarget() {
        return target;
    }

    public MethodConstant getMethod() {
        return method;
    }

    public boolean isAsync() {
        return nodeType == InvokeAsyncExpr;
    }

    @Override
    public boolean isConditional() {
        MethodStructure struct = (MethodStructure) method.getComponent();
        return struct.isConditionalReturn();
    }

    @Override
    public NodeType nodeType() {
        return nodeType;
    }

    @Override
    protected void readBody(DataInput in, ConstantResolver res)
            throws IOException {
        super.readBody(in, res);

        method = (MethodConstant) res.getConstant(readMagnitude(in));
        target = readExprAST(in, res);
    }

    @Override
    public void prepareWrite(ConstantResolver res) {
        super.prepareWrite(res);

        method = (MethodConstant) res.register(method);
        target.prepareWrite(res);
    }

    @Override
    protected void writeBody(DataOutput out, ConstantResolver res)
            throws IOException {
        super.writeBody(out, res);

        writePackedLong(out, res.indexOf(method));
        target.writeExpr(out, res);
    }

    @Override
    public String toString() {
        return target.toString() + '.' + method.getName() +
                (isAsync() ? "^" : "") + super.toString();
    }
}