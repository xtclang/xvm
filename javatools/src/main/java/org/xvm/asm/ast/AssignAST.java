package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.ast.LanguageAST.ExprAST;

import static org.xvm.asm.ast.LanguageAST.NodeType.Assign;
import static org.xvm.asm.ast.LanguageAST.NodeType.BinOpAssign;

import static org.xvm.util.Handy.readMagnitude;


/**
 * Allocate a register, i.e. declare a local variable. This AST node is only an "expression" in the
 * sense that the variable (the register itself) can be used as an expression.
 */
public class AssignAST<C>
        extends ExprAST<C> {

    ExprAST<C> lhs;
    Operator   op;
    ExprAST<C> rhs;

    public enum Operator {
        Asn           ("="   ),     // includes "<-" expression
        AddAsn        ("+="  ),
        SubAsn        ("-="  ),
        MulAsn        ("*="  ),
        DivAsn        ("/="  ),
        ModAsn        ("%="  ),
        ShiftLAsn     ("<<=" ),
        ShiftRAsn     (">>=" ),
        UShiftRAsn    (">>>="),
        AndAsn        ("&="  ),
        OrAsn         ("|="  ),
        XorAsn        ("^="  ),
        CondAndAsn    ("&&=" ),
        CondOrAsn     ("||=" ),
        CondAsn       (":="  ),
        CondNotNullAsn("?="  ),
        CondElseAsn   ("?:=" ),
        ;

        public final String text;

        Operator(String text) {
            this.text = text;
        }

        /**
         * Look up an Operator enum by its ordinal.
         *
         * @param i  the ordinal
         *
         * @return the Operator for the specified ordinal
         */
        public static Operator valueOf(int i) {
            return OPERATORS[i];
        }

        /**
         * All of the Operator enums.
         */
        private static final Operator[] OPERATORS = Operator.values();
    }

    /**
     * @param binOp  true indicates an "in place binary assignment operator"; otherwise it's just
     *               the simple assignment operator
     */
    AssignAST(boolean binOp) {
        op = binOp ? null : Operator.Asn;
    }

    /**
     * @param lhs   left-hand-side expression to assign to
     * @param op    assignment operator
     * @param rhs   right-hand-side expression to assign to
     */
    public AssignAST(ExprAST<C> lhs, Operator op, ExprAST<C> rhs) {
        assert lhs != null && op != null && rhs != null;
        this.lhs = lhs;
        this.op  = op;
        this.rhs = rhs;
    }

    @Override
    public int getCount() {
        return lhs.getCount();
    }

    @Override
    public C getType(int i) {
        return lhs.getType(i);
    }

    @Override public NodeType nodeType() {
        return op == Operator.Asn ? Assign : BinOpAssign;
    }

    @Override
    public void read(DataInput in, ConstantResolver<C> res)
            throws IOException {
        lhs = deserializeExpr(in, res);
        if (nodeType() != Assign) {
            op = Operator.valueOf(readMagnitude(in));
        }
        rhs = deserializeExpr(in, res);
    }

    @Override
    public void prepareWrite(ConstantResolver<C> res) {
        lhs.prepareWrite(res);
        rhs.prepareWrite(res);
    }

    @Override
    public void write(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        out.writeByte(nodeType().ordinal());
        lhs.writeExpr(out, res);
        rhs.writeExpr(out, res);
    }

    @Override
    public String toString() {
        return lhs + " " + op.text + " " + rhs;
    }
}