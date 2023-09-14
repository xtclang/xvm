package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static org.xvm.asm.ast.BinaryAST.NodeType.CallAsyncExpr;
import static org.xvm.asm.ast.BinaryAST.NodeType.CallExpr;


/**
 * Call expression for "not constant" function call.
 */
public class CallExprAST<C>
        extends CallableExprAST<C> {

    private final NodeType nodeType;
    private ExprAST<C>     function;

    CallExprAST(NodeType nodeType) {
        this.nodeType = nodeType;
    }

    /**
     * Construct an InvokeExprAST.
     */
    public CallExprAST(ExprAST<C> function, C[] retTypes, ExprAST<C>[] args, boolean async) {
        super(retTypes, args);

        assert function != null;

        this.nodeType = async ? CallAsyncExpr : CallExpr;
        this.function = function;
    }

    public ExprAST<C> getFunction() {
        return function;
    }

    public boolean isAsync() {
        return nodeType == CallAsyncExpr;
    }

    @Override
    public NodeType nodeType() {
        return nodeType;
    }

    @Override
    protected void readBody(DataInput in, ConstantResolver<C> res)
            throws IOException {
        super.readBody(in, res);

        function = readExprAST(in, res);
    }

    @Override
    public void prepareWrite(ConstantResolver<C> res) {
        super.prepareWrite(res);

        function.prepareWrite(res);
    }

    @Override
    protected void writeBody(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        super.writeBody(out, res);

        function.writeExpr(out, res);
    }

    @Override
    public String dump() {
        return function.dump() + (isAsync() ? "^\n(" : "\n(") + super.dump() + ")\n";
    }

    @Override
    public String toString() {
        return function.toString() + (isAsync() ? "^(" : "(") + super.toString() + ')';
    }
}