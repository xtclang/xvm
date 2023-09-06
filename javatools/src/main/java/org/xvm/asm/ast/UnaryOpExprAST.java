package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * An expressions that follows the pattern "operator expression" or "expression operator" and may
 * change the type of the underlying expression.
 */
public class UnaryOpExprAST<C>
        extends UnaryExprAST<C> {
    private Operator op;

    public enum Operator {
        Plus      ("+",        true),
        Minus     ("-",        true),
        Not       ("!",        true),
        Compl     ("~",        true),
        PreInc    ("++",       true),
        PreDec    ("--",       true),
        PostInc   ("++",       false),
        PostDec   ("--",       false),
        Ref       ("&",        true),
        Var       ("&",        true),
        Type      ("typeOf:",  true),
        Private   ("private:", true),
        Protected ("private:", true),
        Public    ("public:",  true),
        Pack      (""       ,  true),
        Unpack    (""       ,  false),
        Convert   (""       ,  false),
        ToInt     (""       ,  false),
        Trace     (""       ,  false),
        ;

        public final String  text;
        public final boolean pre;

        Operator(String text, boolean pre) {
            this.text = text;
            this.pre  = pre;
        }
    }

    UnaryOpExprAST() {}

    public UnaryOpExprAST(ExprAST<C> expr, Operator op, C type) {
        super(expr, type);

        assert op != Operator.Not;
        this.op = op;
    }

    public Operator getOp() {
        return op;
    }

    @Override
    public NodeType nodeType() {
        return NodeType.UnaryOpExpr;
    }

    @Override
    public void read(DataInput in, ConstantResolver<C> res)
            throws IOException {
        super.read(in, res);

        op = UnaryOpExprAST.Operator.values()[readMagnitude(in)];
    }

    @Override
    public void write(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        super.write(out, res);

        writePackedLong(out, op.ordinal());
    }

    @Override
    public String dump() {
        return op.pre
            ? op.text + getExpr().dump()
            : getExpr().dump() + op.text;
    }

    @Override
    public String toString() {
        return op.pre
            ? op.text + getExpr()
            : getExpr() + op.text;
    }
}