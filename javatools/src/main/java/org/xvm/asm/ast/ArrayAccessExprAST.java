package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.ast.LanguageAST.ExprAST;


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
    public NodeType nodeType() {
        return NodeType.ArrayAccessExpr;
    }

    @Override
    public void read(DataInput in, ConstantResolver<C> res)
            throws IOException {
        array = deserialize(in, res);
        index = deserialize(in, res);
    }

    @Override
    public void prepareWrite(ConstantResolver<C> res) {
        array.prepareWrite(res);
        index.prepareWrite(res);
    }

    @Override
    public void write(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        out.writeByte(nodeType().ordinal());

        array.write(out, res);
        index.write(out, res);
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