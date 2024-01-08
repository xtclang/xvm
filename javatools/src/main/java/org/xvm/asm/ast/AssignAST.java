package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.TypeConstant;

import static org.xvm.asm.ast.BinaryAST.NodeType.Assign;
import static org.xvm.asm.ast.BinaryAST.NodeType.BinOpAssign;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Assign an "L-Value", i.e. store a value in a variable, a property, an array element, etc.
 * This AST node is only an "expression" in the sense that the "left hand side" can itself be used
 * as an expression.
 */
public class AssignAST
        extends ExprAST {

    ExprAST        lhs;
    Operator       op;
    ExprAST        rhs;
    MethodConstant method;

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
        AsnIfNotFalse (":="  ),     // x := y; (includes when used as a condition, e.g. if (x := y))
        AsnIfNotNull  ("?="  ),     // x ?= y; (includes when used as a condition, e.g. if (x := y))
        AsnIfWasTrue  ("&&=" ),
        AsnIfWasFalse ("||=" ),
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
    public AssignAST(ExprAST lhs, Operator op, ExprAST rhs) {
        this(lhs, op, rhs, null);
    }

    /**
     * @param lhs    left-hand-side expression to assign to
     * @param op     assignment operator
     * @param rhs    right-hand-side expression to assign to
     * @param method (optional) method to use for the operation
     */
    public AssignAST(ExprAST lhs, Operator op, ExprAST rhs, MethodConstant method) {
        assert lhs != null && lhs.isAssignable() && op != null && rhs != null;
        this.lhs    = lhs;
        this.op     = op;
        this.rhs    = rhs;
        this.method = method;
    }

    public ExprAST getLValue() {
        return lhs;
    }

    public Operator getOperator() {
        return op;
    }

    public ExprAST getRValue() {
        return rhs;
    }

    public MethodConstant getMethod() {
        return method;
    }

    @Override
    public NodeType nodeType() {
        return op == Operator.Asn ? Assign : BinOpAssign;
    }

    @Override
    public int getCount() {
        return lhs.getCount();
    }

    @Override
    public TypeConstant getType(int i) {
        return lhs.getType(i);
    }

    @Override
    protected void readBody(DataInput in, ConstantResolver res)
            throws IOException {
        lhs = readExprAST(in, res);
        if (nodeType() != Assign) {
            op = Operator.valueOf(readMagnitude(in));
        }
        rhs = readExprAST(in, res);

        int methodId = readPackedInt(in);
        method = methodId < 0
            ? null
            : (MethodConstant) res.getConstant(methodId);
    }

    @Override
    public void prepareWrite(ConstantResolver res) {
        lhs.prepareWrite(res);
        rhs.prepareWrite(res);
        method = (MethodConstant) res.register(method);
    }

    @Override
    protected void writeBody(DataOutput out, ConstantResolver res)
            throws IOException {
        lhs.writeExpr(out, res);
        if (nodeType() != Assign) {
            writePackedLong(out, op.ordinal());
        }
        rhs.writeExpr(out, res);
        if (method == null) {
            writePackedLong(out, -1);
        } else {
            writePackedLong(out, res.indexOf(method));
        }
    }

    @Override
    public String toString() {
        return lhs + " " + op.text + " " + rhs;
    }
}