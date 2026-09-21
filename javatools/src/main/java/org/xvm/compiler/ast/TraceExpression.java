package org.xvm.compiler.ast;

import java.util.List;

import org.xvm.asm.Argument;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.Register;

import org.xvm.asm.constants.TypeConstant;

import static org.xvm.asm.ErrorListener.Silence.PROBE;
import static org.xvm.asm.ErrorListener.silent;

/**
 * An expression that holds a copy of the result of another expression in order to provide optional
 * traceability.
 */
public class TraceExpression
        extends SyntheticExpression {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a TraceExpression.
     *
     * @param expr  the expression to trace
     */
    public TraceExpression(Expression expr) {
        super(expr);

        assert expr.isValidated();

        finishValidations(null, null, expr.getTypes(), expr.getTypeFit(), expr.toConstants(), silent(PROBE));
    }

    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return the traceable arguments resulting from this expression, available after the code has
     *         been emitted for the underlying expression
     */
    public Argument[] getArguments() {
        assert m_aArgs != null;
        return m_aArgs;
    }

    // ----- Expression compilation ----------------------------------------------------------------

    @Override
    protected boolean hasSingleValueImpl() {
        return expr.hasSingleValueImpl();
    }

    @Override
    protected boolean hasMultiValueImpl() {
        return expr.hasMultiValueImpl();
    }

    @Override
    public TypeConstant getImplicitType(Context ctx) {
        return getType();
    }

    @Override
    public TypeConstant[] getImplicitTypes(Context ctx) {
        return getTypes();
    }

    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, ErrorListener errs) {
        return this;
    }

    @Override
    protected Expression validateMulti(Context ctx, TypeConstant[] atypeRequired, ErrorListener errs) {
        return this;
    }

    @Override
    public void generateVoid(Context ctx, Code code, ErrorListener errs) {
        genCode(ctx, code, errs);
    }

    @Override
    public Argument generateArgument(Context ctx, Code code, boolean fLocalPropOk, ErrorListener errs) {
        if (isConstant() || !isSingle()) {
            genCode(ctx, code, errs);
            return m_aArgs[0];
        }

        Argument   arg  = expr.generateArgument(ctx, code, fLocalPropOk, errs);
        Assignable LVal = createTempVar(code, getType());
        LVal.assign(arg, code, errs);
        m_aArgs = new Argument[] {LVal.getRegister()};

        // properties and dynamic Ref/Var registers must not be read a second time, but a regular
        // register can be - it simplifies the type inference analysis by the JIT compiler
        return arg instanceof Register reg && reg.isNormal() ? arg : m_aArgs[0];
    }

    @Override
    protected Argument ensurePointInTime(Code code, Argument arg, List<Expression> listExprs, int iExpr) {
        return expr.ensurePointInTime(code, arg, listExprs, iExpr);
    }

    @Override
    protected Argument ensurePointInTime(Code code, Argument arg, Expression exprRight) {
        return expr.ensurePointInTime(code, arg, exprRight);
    }

    @Override
    public Argument[] generateArguments(Context ctx, Code code, boolean fLocalPropOk, ErrorListener errs) {
        genCode(ctx, code, errs);
        return m_aArgs;
    }

    @Override
    public void generateAssignment(Context ctx, Code code, Assignable LVal, ErrorListener errs) {
        genCode(ctx, code, errs);
        LVal.assign(m_aArgs[0], code, errs);
    }

    @Override
    public void generateAssignments(Context ctx, Code code, Assignable[] aLVal, ErrorListener errs) {
        genCode(ctx, code, errs);
        for (int i = 0, c = aLVal.length; i < c; ++i) {
            aLVal[i].assign(m_aArgs[i], code, errs);
        }
    }

    private void genCode(Context ctx, Code code, ErrorListener errs) {
        if (isConstant()) {
            m_aArgs = toConstants();
        } else {
            TypeConstant[] aTypes = getTypes();
            int            cTypes = aTypes.length;
            Assignable[]   aLVals = new Assignable[cTypes];
            Register[]     aRegs  = new Register[cTypes];
            for (int i = 0; i < cTypes; ++i) {
                TypeConstant type = aTypes[i];
                Assignable   LVal = createTempVar(code, type);

                aLVals[i] = LVal;
                aRegs [i] = LVal.getRegister();
            }

            m_aArgs = aRegs;
            expr.generateAssignments(ctx, code, aLVals, errs);
        }
    }

    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString() {
        return expr.toString();
    }

    // ----- fields --------------------------------------------------------------------------------

    /**
     * The traceable arguments resulting from this expression.
     */
    private Argument[] m_aArgs;
}
