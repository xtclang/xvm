package org.xvm.compiler.ast;


import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Op;
import org.xvm.asm.Register;

import org.xvm.asm.ast.ConstantExprAST;
import org.xvm.asm.ast.ConvertExprAST;
import org.xvm.asm.ast.ExprAST;

import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.Invoke_01;
import org.xvm.asm.op.JumpFalse;
import org.xvm.asm.op.Label;
import org.xvm.asm.op.Var;


/**
 * A type conversion expression. This converts values from the sub-expression into values of
 * different types.
 */
public class ConvertExpression
        extends SyntheticExpression
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a ConvertExpression that will convert a result from the passed expression to a
     * different type using the provided method.
     *
     * @param expr     the expression that yields the raw results
     * @param iVal     the index of the raw result that needs conversion
     * @param aidConv  the conversion methods (some can have nulls, indicating no-conversion)
     * @param errs     the ErrorListener to log errors to
     */
    public ConvertExpression(Expression expr, MethodConstant[] aidConv, ErrorListener errs)
        {
        super(expr);

        assert aidConv != null && aidConv.length >= 1;
        for (MethodConstant idConv : aidConv)
            {
            if (idConv != null)
                {
                assert idConv.getRawParams().length == 0
                    || idConv.getComponent() instanceof MethodStructure method
                        && method.getRequiredParamCount() == 0;
                assert idConv.getRawReturns().length > 0;
                assert !idConv.getComponent().isStatic();
                }
            }

        m_aidConv = aidConv;

        if (expr.isSingle())
            {
            assert aidConv[0] != null;

            TypeConstant type = aidConv[0].getRawReturns()[0];
            Constant     val  = null;
            if (expr.isConstant())
                {
                // determine if compile-time conversion is supported
                val = convertConstant(expr.toConstant(), type);
                }

            finishValidation(null, null, type, expr.getTypeFit().addConversion(), val, errs);
            }
        else
            {
            Constant[]     aVal  = null;
            TypeConstant[] aType = expr.getTypes().clone();
            for (int i = 0, c = aidConv.length; i < c; i++)
                {
                MethodConstant idConv = aidConv[i];
                if (idConv != null)
                    {
                    aType[i] = idConv.getRawReturns()[0];
                    }
                }

            if (expr.isConstant())
                {
                aVal = expr.toConstants().clone();
                for (int i = 0, c = aType.length; i < c; i++)
                    {
                    MethodConstant idConv = aidConv[i];
                    if (idConv != null)
                        {
                        Constant constNew = convertConstant(aVal[i], aType[i]);
                        if (constNew == null)
                            {
                            // there is no compile-time conversion available; continue with run-time
                            // conversion
                            // TODO GG: remove the soft assert below
                            System.err.println("No conversion found for " + aVal[i]);
                            }
                        aVal[i] = constNew;
                        }
                    }
                }

            finishValidations(null, null, aType, expr.getTypeFit().addConversion(), aVal, errs);
            }
        }


    // ----- Expression compilation ----------------------------------------------------------------

    @Override
    public boolean isConditionalResult()
        {
        return expr.isConditionalResult();
        }

    @Override
    protected boolean hasMultiValueImpl()
        {
        return true;
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
        getUnderlyingExpression().generateVoid(ctx, code, errs);
        }

    @Override
    public void generateAssignment(Context ctx, Code code, Assignable LVal, ErrorListener errs)
        {
        Expression     expr   = getUnderlyingExpression();
        MethodConstant idConv = m_aidConv[0];
        if (idConv == null)
            {
            expr.generateAssignment(ctx, code, LVal, errs);
            return;
            }

        // get the value to be converted
        Argument argIn = expr.generateArgument(ctx, code, true, true, errs);

        // determine the destination of the conversion
        if (LVal.isLocalArgument())
            {
            code.add(new Invoke_01(argIn, idConv, LVal.getLocalArgument()));
            }
        else
            {
            Register regResult = new Register(getType(), null, Op.A_STACK);
            code.add(new Invoke_01(argIn, idConv, regResult));
            LVal.assign(regResult, code, errs);
            }
        }

    @Override
    public void generateAssignments(Context ctx, Code code, Assignable[] aLVal, ErrorListener errs)
        {
        int cVals = aLVal.length;
        if (cVals == 1)
            {
            generateAssignment(ctx, code, aLVal[0], errs);
            return;
            }

        // replace the LVals to convert into with a temp, and ask the underlying expression to fill
        // in the resulting set of LVals, and then convert that one value
        MethodConstant[] aidConv   = m_aidConv;
        int              cConvs    = aidConv.length;
        Expression       expr      = getUnderlyingExpression();
        Assignable[]     aLValTemp = aLVal.clone();

        // create a temporary to hold the Boolean result for a conditional call, if necessary
        boolean  fCond   = isConditionalResult();
        Register regCond = null;
        Label    lblSkip = new Label("skip_conv");
        if (fCond)
            {
            Assignable aLValCond = aLValTemp[0];
            if (aLValCond.isNormalVariable())
                {
                regCond = aLValCond.getRegister();
                }
            else
                {
                regCond = code.createRegister(pool().typeBoolean());
                code.add(new Var(regCond));
                aLValTemp[0] = new Assignable(regCond);
                }
            }

        for (int i = 0; i < cConvs; i++)
            {
            MethodConstant idConv = aidConv[i];
            if (idConv != null)
                {
                Register regTemp = code.createRegister(expr.getTypes()[i]);
                code.add(new Var(regTemp));
                aLValTemp[i] = new Assignable(regTemp);
                }
            }

        // generate the pre-converted values
        expr.generateAssignments(ctx, code, aLValTemp, errs);

        // skip the conversion if the conditional result was False
        if (fCond)
            {
            if (aLVal[0] != aLValTemp[0])
                {
                aLVal[0].assign(regCond, code, errs);
                }
            code.add(new JumpFalse(regCond, lblSkip));
            }

        for (int i = 0; i < cConvs; i++)
            {
            MethodConstant idConv = aidConv[i];
            if (idConv != null)
                {
                Register   regTemp = aLValTemp[i].getRegister();
                Assignable LVal    = aLVal[i];
                if (LVal.isLocalArgument())
                    {
                    code.add(new Invoke_01(regTemp, idConv, LVal.getLocalArgument()));
                    }
                else
                    {
                    Register regResult = new Register(getTypes()[i], null, Op.A_STACK);
                    code.add(new Invoke_01(regTemp, idConv, regResult));
                    LVal.assign(regResult, code, errs);
                    }
                }
            }

        if (fCond)
            {
            code.add(lblSkip);
            }
        }

    @Override
    public ExprAST getExprAST()
        {
        return isConstant()
                ? new ConstantExprAST(toConstant())
                : new ConvertExprAST(expr.getExprAST(), getTypes(), m_aidConv);
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        Expression       expr    = getUnderlyingExpression();
        MethodConstant[] aidConv = m_aidConv;
        if (expr.isSingle())
            {
            return expr.toString() + '.' + aidConv[0].getName() +
                    (isValidated()
                        ? '<' + getType().getValueString() + ">()"
                        : "<?>()");
            }
        else
            {
            StringBuilder sb    = new StringBuilder("(");
            boolean       fCond = isConditionalResult();
            for (int i = 0, c = aidConv.length; i < c; i++)
                {
                MethodConstant idConv = aidConv[i];
                if (i > (fCond ? 1 : 0))
                    {
                    sb.append(", ");
                    }

                if (idConv == null)
                    {
                    if (i == 0 && fCond)
                        {
                        sb.append("conditional ");
                        }
                    else
                        {
                        sb.append(expr)
                          .append("[").append(i).append("]");
                        }
                    }
                else
                    {
                    sb.append(expr)
                      .append("[").append(i).append("].")
                      .append(idConv.getName());

                    if (isValidated())
                        {
                        sb.append('<')
                          .append(getTypes()[i].getValueString())
                          .append(">()");
                        }
                    else
                        {
                        sb.append("<?>()");
                        }
                    }
                }
            return sb.append(')').toString();
            }
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The conversion methods.
     */
    private final MethodConstant[] m_aidConv;
    }