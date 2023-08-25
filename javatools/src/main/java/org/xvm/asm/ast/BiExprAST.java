package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.ast.LanguageAST.ExprAST;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * A base class for expressions that follow the pattern "expression operator expression".
 */
public abstract class BiExprAST<C>
        extends ExprAST<C> {

    private Operator   op;
    private ExprAST<C> expr1;
    private ExprAST<C> expr2;

    public enum Operator {
        Colon       (":"   ), // an "else" for nullability checks
        CondElse    ("?:"  ), // the "elvis" operator
        CondOr      ("||"  ),
        BitOr       ("|"   ),
        BitXor      ("^"   ),
        BitAnd      ("&"   ),
        CompEq      ("=="  ),
        CompNeq     ("!="  ),
        CompLt      ("<"   ),
        CompGt      (">"   ),
        CompLtEq    ("<="  ),
        CompGtEq    (">="  ),
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

    protected BiExprAST(Operator op, ExprAST<C> expr1, ExprAST<C> expr2) {
        assert op != null && expr1 != null && expr2 != null;
        this.op    = op;
        this.expr1 = expr1;
        this.expr2 = expr2;
    }

    public Operator getOp() {
        return op;
    }

    public ExprAST<C> getExpr1() {
        return expr1;
    }

    public ExprAST<C> getExpr2() {
        return expr2;
    }

    @Override
    public abstract NodeType nodeType();

    @Override
    public void read(DataInput in, ConstantResolver<C> res)
            throws IOException {
        op    = Operator.values()[readMagnitude(in)];
        expr1 = deserialize(in, res);
        expr2 = deserialize(in, res);
    }

    @Override
    public void prepareWrite(ConstantResolver<C> res) {
        expr1.prepareWrite(res);
        expr2.prepareWrite(res);
    }

    @Override
    public void write(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        out.writeByte(nodeType().ordinal());

        writePackedLong(out, op.ordinal());
        expr1.write(out, res);
        expr2.write(out, res);
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