package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static org.xvm.asm.ast.BinaryAST.NodeType.CallExpr;


/**
 * Call expression for "not constant" function call.
 */
public class CallExprAST<C>
        extends CallableExprAST<C> {

    private ExprAST<C> function;

    CallExprAST() {
    }

    /**
     * Construct an InvokeExprAST.
     */
    public CallExprAST(ExprAST<C> function, C[] retTypes, ExprAST<C>[] args) {
        super(retTypes, args);

        assert function != null;

        this.function = function;
    }

    @Override
    public NodeType nodeType() {
        return CallExpr;
    }

    public ExprAST<C> getFunction() {
        return function;
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
        return function.dump() + "\n(" + super.dump() + ")\n";
    }

    @Override
    public String toString() {
        return function.toString() + '(' + super.toString() + ')';
    }
}