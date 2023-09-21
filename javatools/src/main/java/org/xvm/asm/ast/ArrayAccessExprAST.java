package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.constants.TypeConstant;


/**
 * A single dimensional array access expression.
 */
public class ArrayAccessExprAST
        extends ExprAST {

    private ExprAST array;
    private ExprAST index;

    ArrayAccessExprAST() {}

    public ArrayAccessExprAST(ExprAST array, ExprAST index) {
        assert array != null && index != null;
        this.array = array;
        this.index = index;
    }

    public ExprAST getArray() {
        return array;
    }

    public ExprAST getIndex() {
        return index;
    }

    @Override
    public TypeConstant getType(int i) {
        assert i == 0;
        return array.getType(0).getParamType(0);
    }

    @Override
    public boolean isAssignable() {
        return true;
    }

    @Override
    public NodeType nodeType() {
        return NodeType.ArrayAccessExpr;
    }

    @Override
    protected void readBody(DataInput in, ConstantResolver res)
            throws IOException {
        array = readExprAST(in, res);
        index = readExprAST(in, res);
    }

    @Override
    public void prepareWrite(ConstantResolver res) {
        array.prepareWrite(res);
        index.prepareWrite(res);
    }

    @Override
    protected void writeBody(DataOutput out, ConstantResolver res)
            throws IOException {
        array.writeExpr(out, res);
        index.writeExpr(out, res);
    }

    @Override
    public String toString() {
        return array.toString() + '[' + index + ']';
    }
}