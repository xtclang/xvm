package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.constants.TypeConstant;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * A base class for expressions that follow the pattern "expression operator expression".
 */
public abstract class BiExprAST
        extends ExprAST {

    private ExprAST  expr1;
    private Operator op;
    private ExprAST  expr2;

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

    protected BiExprAST(ExprAST expr1, Operator op, ExprAST expr2) {
        assert expr1 != null && op != null && expr2 != null;
        this.expr1 = expr1;
        this.op    = op;
        this.expr2 = expr2;
    }

    public ExprAST getExpr1() {
        return expr1;
    }

    public Operator getOp() {
        return op;
    }

    public ExprAST getExpr2() {
        return expr2;
    }

    @Override
    public abstract TypeConstant getType(int i);

    @Override
    public abstract NodeType nodeType();

    @Override
    protected void readBody(DataInput in, ConstantResolver res)
            throws IOException {
        expr1 = readExprAST(in, res);
        op    = Operator.values()[readMagnitude(in)];
        expr2 = readExprAST(in, res);
    }

    @Override
    public void prepareWrite(ConstantResolver res) {
        expr1.prepareWrite(res);
        expr2.prepareWrite(res);
    }

    @Override
    protected void writeBody(DataOutput out, ConstantResolver res)
            throws IOException {
        expr1.writeExpr(out, res);
        writePackedLong(out, op.ordinal());
        expr2.writeExpr(out, res);
    }

    @Override
    public String toString() {
        return op == Operator.As
                ? expr1 + ".as(" + expr2 + ')'
                : expr1 + " " + op.text + " " + expr2;
    }
}