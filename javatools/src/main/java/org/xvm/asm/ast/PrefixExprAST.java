package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.ast.BinaryAST.ExprAST;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * A base class for expressions that follow the pattern "operator expression".
 */
public abstract class PrefixExprAST<C>
        extends ExprAST<C> {

    private Operator   op;
    private ExprAST<C> expr;

    public enum Operator {
        Plus  ("+" ),
        Minus ("-" ),
        Not   ("!" ),
        Compl ("~" ),
        Inc   ("++"),
        Dec   ("--"),
        ;

        public final String text;

        Operator(String text) {
            this.text = text;
        }
    }

    PrefixExprAST() {}

    protected PrefixExprAST(Operator op, ExprAST<C> expr) {
        assert op != null && expr != null;
        this.op    = op;
        this.expr = expr;
    }

    public Operator getOp() {
        return op;
    }

    public ExprAST<C> getExpr() {
        return expr;
    }

    @Override
    public abstract C getType(int i);

    @Override
    public abstract NodeType nodeType();

    @Override
    public void read(DataInput in, ConstantResolver<C> res)
            throws IOException {
        op   = Operator.values()[readMagnitude(in)];
        expr = readExprAST(in, res);
    }

    @Override
    public void prepareWrite(ConstantResolver<C> res) {
        expr.prepareWrite(res);
    }

    @Override
    public void write(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        out.writeByte(nodeType().ordinal());

        writePackedLong(out, op.ordinal());
        expr.writeExpr(out, res);
    }

    @Override
    public String dump() {
        return op.text + " " + expr.dump();
    }

    @Override
    public String toString() {
        return op.text + expr;
    }
}