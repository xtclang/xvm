package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static org.xvm.asm.ast.BinaryAST.NodeType.Assign;
import static org.xvm.asm.ast.BinaryAST.NodeType.BinOpAssign;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Assign an "L-Value", i.e. store a value in a variable, a property, an array element, etc.
 * This AST node is only an "expression" in the sense that the "left hand side" can itself be used
 * as an expression.
 */
public class AssignAST<C>
        extends ExprAST<C> {

    ExprAST<C> lhs;
    Operator   op;
    ExprAST<C> rhs;

    public enum Operator {
        Asn           ("="   ),     // includes "<-" expression
        CondAsn       (":="  ),     // if (x := expr) {...}, for (x : expr), hidden Boolean lvalue
        CondNotNullAsn("?="  ),     // if (x ?= expr) {...}, hidden Boolean lvalue
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
        AsnIfWasTrue  ("&&=" ),
        AsnIfWasFalse ("||=" ),
        AsnIfNotFalse (":="  ),     // x := y; (note: this is not used for a condition, e.g. if)
        AsnIfNotNull  ("?="  ),     // x ?= y; (note: this is not used for a condition, e.g. if)
        AsnIfWasNull  ("?:=" ),
        Deref         ("->"  ),
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
        assert lhs != null /* TODO GG && lhs.isAssignable() */ && op != null && rhs != null;
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

    public AssignAST<C> makeCondition(RegAllocAST<C> allocBoolean) {
        Operator newOp;
        if (op == Operator.AsnIfNotFalse) {
            newOp = Operator.CondAsn;
        } else if (op == Operator.AsnIfNotNull) {
            newOp = Operator.CondNotNullAsn;
        } else {
            throw new IllegalStateException("unsupported op: " + op);
        }

        ExprAST<C>[] lvals;
        if (lhs instanceof MultiExprAST<C> multi) {
            int oldCount = multi.getCount();
            int newCount = oldCount + 1;
            lvals = new ExprAST[newCount];
            System.arraycopy(multi.getExprs(), 0, lvals, 1, oldCount);
        } else {
            lvals = new ExprAST[2];
            lvals[1] = lhs;
        }
        lvals[0] = allocBoolean;
        return new AssignAST<>(new MultiExprAST<>(lvals), newOp, rhs);
    }

    @Override
    public NodeType nodeType() {
        return op == Operator.Asn ? Assign : BinOpAssign;
    }

    @Override
    protected void readBody(DataInput in, ConstantResolver<C> res)
            throws IOException {
        lhs = readExprAST(in, res);
        if (nodeType() != Assign) {
            op = Operator.valueOf(readMagnitude(in));
        }
        rhs = readExprAST(in, res);
    }

    @Override
    public void prepareWrite(ConstantResolver<C> res) {
        lhs.prepareWrite(res);
        rhs.prepareWrite(res);
    }

    @Override
    protected void writeBody(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        lhs.writeExpr(out, res);
        if (nodeType() != Assign) {
            writePackedLong(out, op.ordinal());
        }
        rhs.writeExpr(out, res);
    }

    @Override
    public String toString() {
        return lhs + " " + op.text + " " + rhs;
    }
}