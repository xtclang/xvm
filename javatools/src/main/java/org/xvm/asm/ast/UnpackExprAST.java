package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.ast.BinaryAST.ExprAST;


/**
 * A tuple un-packing expression.
 */
public class UnpackExprAST<C>
        extends ExprAST<C> {

    private ExprAST<C> tupleExpr;

    UnpackExprAST() {}

    public UnpackExprAST(ExprAST<C> tupleExpr) {
        this.tupleExpr = tupleExpr;
    }

    public ExprAST<C> getTupleExpr() {
        return tupleExpr;
    }

    @Override
    public C getType(int i) {
        return tupleExpr.getType(i);
    }

    @Override
    public NodeType nodeType() {
        return NodeType.UnpackExpr;
    }

    @Override
    public void read(DataInput in, ConstantResolver<C> res)
            throws IOException {
        tupleExpr = readAST(in, res);
    }

    @Override
    public void prepareWrite(ConstantResolver<C> res) {
        tupleExpr.prepareWrite(res);
    }

    @Override
    public void write(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        out.writeByte(nodeType().ordinal());

        tupleExpr.write(out, res);
    }

    @Override
    public String dump() {
        return "unpack: " + tupleExpr.dump();
    }

    @Override
    public String toString() {
        return "unpack: " + tupleExpr;
    }
}