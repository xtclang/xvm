package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * A base class for expressions that follow the pattern "expression operator expression".
 */
public abstract class BiExprAST<C>
        extends ExprAST<C> {

    private ExprAST<C> expr1;
    private Operator   op;
    private ExprAST<C> expr2;

    public enum Operator {
        Else        (":"   ), // an "else" for nullability checks
        CondElse    ("?:"  ), // the "elvis" operator
        CondOr      ("||"  ),
        CondXor     ("^^"  ),
        CondAnd     ("&&"  ),
        BitOr       ("|"   ),
        BitXor      ("^"   ),
        BitAnd      ("&"   ),
        CompEq      ("=="  ),
        CompNeq     ("!="  ),
        CompLt      ("<"   ),
        CompGt      (">"   ),
        CompLtEq    ("<="  ),
        CompGtEq    (">="  ),
        CompOrd     ("<=>" ),
        As          ("as"  ),
        Is          ("is"  ),
        RangeII     (".."  ),
        RangeIE     ("..<" ),
        RangeEI     (">.." ),
        RangeEE     (">..<"),
        Shl         ("<<"  ),
        Shr         (">>"  ),
        Ushr        (">>>" ),
        Add         ("+"   ),
        Sub         ("-"   ),
        Mul         ("*"   ),
        Div         ("/"   ),
        Mod         ("%"   ),
        DivRem      ("/%"  ),
        ;

        public final String text;

        Operator(String text) {
            this.text = text;
        }
    }

    BiExprAST() {}

    protected BiExprAST(ExprAST<C> expr1, Operator op, ExprAST<C> expr2) {
        assert expr1 != null && op != null && expr2 != null;
        this.expr1 = expr1;
        this.op    = op;
        this.expr2 = expr2;
    }

    public ExprAST<C> getExpr1() {
        return expr1;
    }

    public Operator getOp() {
        return op;
    }

    public ExprAST<C> getExpr2() {
        return expr2;
    }

    @Override
    public abstract C getType(int i);

    @Override
    public abstract NodeType nodeType();

    @Override
    protected void readBody(DataInput in, ConstantResolver<C> res)
            throws IOException {
        expr1 = readExprAST(in, res);
        op    = Operator.values()[readMagnitude(in)];
        expr2 = readExprAST(in, res);
    }

    @Override
    public void prepareWrite(ConstantResolver<C> res) {
        expr1.prepareWrite(res);
        expr2.prepareWrite(res);
    }

    @Override
    protected void writeBody(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        expr1.writeExpr(out, res);
        writePackedLong(out, op.ordinal());
        expr2.writeExpr(out, res);
    }

    @Override
    public String dump() {
        return expr1.dump() + " " + op.text + " " + expr2.dump();
    }

    @Override
    public String toString() {
        return expr1 + " " + op.text + " " + expr2;
    }
}