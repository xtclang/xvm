package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;


/**
 * A single dimensional array access expression.
 */
public class ArrayAccessExprAST<C>
        extends ExprAST<C> {

    private ExprAST<C> array;
    private ExprAST<C> index;

    ArrayAccessExprAST() {}

    public ArrayAccessExprAST(ExprAST<C> array, ExprAST<C> index) {
        assert array != null && index != null;
        this.array = array;
        this.index = index;
    }

    public ExprAST<C> getArray() {
        return array;
    }

    public ExprAST<C> getIndex() {
        return index;
    }

    @Override
    public C getType(int i) {
        assert i == 0;
        return array.getType(i); // TODO GG: .getSubType(0);
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
    protected void readBody(DataInput in, ConstantResolver<C> res)
            throws IOException {
        array = readExprAST(in, res);
        index = readExprAST(in, res);
    }

    @Override
    public void prepareWrite(ConstantResolver<C> res) {
        array.prepareWrite(res);
        index.prepareWrite(res);
    }

    @Override
    protected void writeBody(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        array.writeExpr(out, res);
        index.writeExpr(out, res);
    }

    @Override
    public String dump() {
        return array.dump() + '[' + index.dump() + ']';
    }

    @Override
    public String toString() {
        return array.toString() + '[' + index + ']';
    }
}