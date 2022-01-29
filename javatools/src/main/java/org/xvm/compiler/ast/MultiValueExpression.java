package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.Compiler;


/**
 * A synthetic expression that converts an array of expressions into a multi-value expression.
 */
public class MultiValueExpression
        extends Expression
    {
    // ----- constructors --------------------------------------------------------------------------

    public MultiValueExpression(Expression[] aExprs, ErrorListener errs)
        {
        exprs = aExprs;
        aExprs[0].getParent().adopt(this);

        int            cExprs = aExprs.length;
        TypeConstant[] aTypes = new TypeConstant[cExprs];
        Constant[]     aVals  = null;
        TypeFit        fit    = TypeFit.Fit;
        for (int i = 0; i < cExprs; ++i)
            {
            Expression expr = aExprs[i];
            adopt(expr);

            aTypes[i] = expr.getType();
            if (i == 0 || aVals != null)
                {
                if (expr.isConstant())
                    {
                    if (i == 0)
                        {
                        aVals = new Constant[cExprs];
                        }
                    aVals[i] = expr.toConstant();
                    }
                else
                    {
                    aVals = null;
                    }
                }
            fit = fit.combineWith(expr.getTypeFit());
            }

        finishValidations(null, null, aTypes, fit, aVals, errs);
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return the array of underlying expressions
     */
    public Expression[] getUnderlyingExpressions()
        {
        return exprs;
        }

    @Override
    public Compiler.Stage getStage()
        {
        Compiler.Stage stageThis = super.getStage();
        Compiler.Stage stageThat = exprs[0].getStage();
        return stageThis.compareTo(stageThat) > 0
                ? stageThis
                : stageThat;
        }

    @Override
    public long getStartPosition()
        {
        return exprs[0].getStartPosition();
        }

    @Override
    public long getEndPosition()
        {
        return exprs[exprs.length-1].getEndPosition();
        }

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }


    // ----- Expression compilation ----------------------------------------------------------------

    @Override
    protected boolean hasSingleValueImpl()
        {
        return false;
        }

    @Override
    protected boolean hasMultiValueImpl()
        {
        return true;
        }

    @Override
    public TypeConstant[] getImplicitTypes(Context ctx)
        {
        return getTypes();
        }

    @Override
    protected Expression validateMulti(Context ctx, TypeConstant[] atypeRequired, ErrorListener errs)
        {
        return this;
        }

    @Override
    public boolean isCompletable()
        {
        for (Expression expr : exprs)
            {
            if (!expr.isCompletable())
                {
                return false;
                }
            }

        return true;
        }

    @Override
    public boolean isShortCircuiting()
        {
        for (Expression expr : exprs)
            {
            if (expr.isShortCircuiting())
                {
                return true;
                }
            }

        return false;
        }

    @Override
    public void generateVoid(Context ctx, Code code, ErrorListener errs)
        {
        for (Expression expr : exprs)
            {
            expr.generateVoid(ctx, code, errs);
            }
        }

    @Override
    public Argument[] generateArguments(
            Context ctx, Code code, boolean fLocalPropOk, boolean fUsedOnce, ErrorListener errs)
        {
        if (isConstant())
            {
            return toConstants();
            }

        Expression[] aExprs = exprs;
        int          cExprs = aExprs.length;
        Argument[]   aArgs  = new Argument[cExprs];
        for (int i = 0; i < cExprs; ++i)
            {
            // only the first argument is allowed to be on-stack
            aArgs[i] = aExprs[i].generateArgument(ctx, code, fLocalPropOk, fUsedOnce && i == 0, errs);
            }
        return aArgs;
        }

    @Override
    public void generateAssignments(Context ctx, Code code, Assignable[] aLVal, ErrorListener errs)
        {
        Expression[] aExprs = exprs;
        int          cExprs = aExprs.length;
        int          cLVals = aLVal.length;
        for (int i = 0; i < cLVals; ++i)
            {
            aExprs[i].generateAssignment(ctx, code, aLVal[i], errs);
            }
        for (int i = cLVals; i < cExprs; ++i)
            {
            aExprs[i].generateVoid(ctx, code, errs);
            }
        }

    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append("multi:(");

        boolean first = true;
        for (Expression expr : exprs)
            {
            if (first)
                {
                first = false;
                }
            else
                {
                sb.append(", ");
                }
            sb.append(expr);
            }

        sb.append(')');

        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Expression[] exprs;

    private static final Field[] CHILD_FIELDS = fieldsForNames(SyntheticExpression.class, "exprs");
    }
