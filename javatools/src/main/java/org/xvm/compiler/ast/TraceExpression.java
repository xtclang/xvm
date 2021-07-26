package org.xvm.compiler.ast;


import org.xvm.asm.Argument;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.Register;

import org.xvm.asm.constants.TypeConstant;


/**
 * An expression that holds a copy of the result of another expression in order to provide optional
 * traceability.
 */
public class TraceExpression
        extends SyntheticExpression
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a TraceExpression.
     *
     * @param expr  the expression to trace
     */
    public TraceExpression(Expression expr)
        {
        super(expr);

        assert expr.isValidated();

        finishValidations(null, null, expr.getTypes(), expr.getTypeFit(), expr.toConstants(),
                ErrorListener.BLACKHOLE);
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return the traceable arguments resulting from this expression, available after the code has
     *         been emitted for the underlying expression
     */
    public Argument[] getArguments()
        {
        assert m_aArgs != null;
        return m_aArgs;
        }


    // ----- Expression compilation ----------------------------------------------------------------

    @Override
    protected boolean hasSingleValueImpl()
        {
        return expr.hasSingleValueImpl();
        }

    @Override
    protected boolean hasMultiValueImpl()
        {
        return expr.hasMultiValueImpl();
        }

    @Override
    public TypeConstant getImplicitType(Context ctx)
        {
        return getType();
        }

    @Override
    public TypeConstant[] getImplicitTypes(Context ctx)
        {
        return getTypes();
        }

    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
        return this;
        }

    @Override
    protected Expression validateMulti(Context ctx, TypeConstant[] atypeRequired, ErrorListener errs)
        {
        return this;
        }

    @Override
    public void generateVoid(Context ctx, Code code, ErrorListener errs)
        {
        genCode(ctx, code, errs);
        }

    @Override
    public Argument generateArgument(
            Context ctx, Code code, boolean fLocalPropOk, boolean fUsedOnce, ErrorListener errs)
        {
        genCode(ctx, code, errs);
        return m_aArgs[0];
        }

    @Override
    public Argument[] generateArguments(Context ctx, Code code, boolean fLocalPropOk,
            boolean fUsedOnce, ErrorListener errs)
        {
        genCode(ctx, code, errs);
        return m_aArgs;
        }

    @Override
    public void generateAssignment(Context ctx, Code code, Assignable LVal, ErrorListener errs)
        {
        genCode(ctx, code, errs);
        LVal.assign(m_aArgs[0], code, errs);
        }

    @Override
    public void generateAssignments(Context ctx, Code code, Assignable[] aLVal, ErrorListener errs)
        {
        genCode(ctx, code, errs);
        for (int i = 0, c = aLVal.length; i < c; ++i)
            {
            aLVal[i].assign(m_aArgs[i], code, errs);
            }
        }

    void genCode(Context ctx, Code code, ErrorListener errs)
        {
        if (isConstant())
            {
            m_aArgs = toConstants();
            }
        else
            {
            TypeConstant[] aTypes = getTypes();
            int            cTypes = aTypes.length;
            Assignable[]   aLVals = new Assignable[cTypes];
            Register[]     aRegs  = new Register[cTypes];
            for (int i = 0; i < cTypes; ++i)
                {
                TypeConstant type = aTypes[i];
                Assignable   LVal = createTempVar(code, type, false);

                aLVals[i] = LVal;
                aRegs [i] = LVal.getRegister();
                }

            m_aArgs = aRegs;
            expr.generateAssignments(ctx, code, aLVals, errs);
            }
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        return expr.toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The traceable arguments resulting from this expression.
     */
    private Argument[] m_aArgs;
    }
