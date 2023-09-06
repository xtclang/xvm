package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.ast.BinaryAST.ExprAST;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * The Narrowed expression refers to an underlying expression with a narrowed type.
 */
public class NarrowedExprAST<C>
        extends ExprAST<C> {

    private ExprAST<C> expr;
    private C          type;

    NarrowedExprAST() {}

    public NarrowedExprAST(ExprAST<C> expr, C type) {
        assert expr != null && type != null;

        this.expr = expr;
        this.type = type;
    }

    public ExprAST<C> getExpr() {
        return expr;
    }

    @Override
    public C getType(int i) {
        assert i == 0;
        return expr.getType(i);
    }

    @Override
    public NodeType nodeType() {
        return NodeType.NarrowedExpr;
    }

    @Override
    public void read(DataInput in, ConstantResolver<C> res)
            throws IOException {
        expr = readExprAST(in, res);
        type = res.getConstant(readMagnitude(in));
    }

    @Override
    public void prepareWrite(ConstantResolver<C> res) {
        expr.prepareWrite(res);
        type = res.register(type);
    }

    @Override
    public void write(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        out.writeByte(nodeType().ordinal());

        expr.writeExpr(out, res);
        writePackedLong(out, res.indexOf(type));
    }

    @Override
    public String dump() {
        return expr.dump() + ".as(" + type + ")";
    }

    @Override
    public String toString() {
        return expr + ".as(" + type + ")";
    }
}