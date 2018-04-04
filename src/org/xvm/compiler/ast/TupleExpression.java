package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.List;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Op.Argument;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.Var_T;

import org.xvm.compiler.Compiler;

import org.xvm.compiler.ast.Statement.Context;

import org.xvm.util.Severity;


/**
 * A tuple expression is an expression containing some number (0 or more) expressions.
 */
public class TupleExpression
        extends Expression
    {
    // ----- constructors --------------------------------------------------------------------------

    public TupleExpression(TypeExpression type, List<Expression> exprs, long lStartPos, long lEndPos)
        {
        this.type        = type;
        this.exprs       = exprs;
        this.m_lStartPos = lStartPos;
        this.m_lEndPos   = lEndPos;
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return get the TypeExpression for the tuple, if any; otherwise return null
     */
    public TypeExpression getTypeExpression()
        {
        return type;
        }

    /**
     * @return the expressions making up the tuple value
     */
    public List<Expression> getExpressions()
        {
        return exprs;
        }

    @Override
    public long getStartPosition()
        {
        return m_lStartPos;
        }

    @Override
    public long getEndPosition()
        {
        return m_lEndPos;
        }

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }


    // ----- compilation ---------------------------------------------------------------------------

    @Override
    public TypeFit testFitMulti(Context ctx, TypeConstant[] atypeRequired, TuplePref pref)
        {
        List<Expression> listExprs = exprs;
        int              cExprs    = listExprs == null ? 0 : listExprs.size();
        int              cRequired = atypeRequired.length;
        if (cRequired == 1 && atypeRequired[0].isTuple() && pref != TuplePref.Required)
            {
            // it is acceptable to ask a tuple expression to be of type tuple, which results in the
            // tuple expression packing itself
            TypeFit fit = testFitMulti(ctx, atypeRequired[0].getParamTypesArray(), TuplePref.Required);
            if (fit.isFit())
                {
                return fit;
                }
            }

        if (cRequired > cExprs)
            {
            // we don't have enough expressions to satisfy the caller
            return TypeFit.NoFit;
            }

        // for each requested type, verify that the underlying expression is willing to provide that
        // type
        TypeFit fitOut = TypeFit.Fit;
        for (int i = 0; i < cExprs; ++i)
            {
            TypeConstant typeRequired = atypeRequired[i];
            Expression   expr         = listExprs.get(i);
            TypeFit      fitExpr      = expr.testFit(ctx, typeRequired, TuplePref.Rejected);
            if (!fitExpr.isFit())
                {
                return TypeFit.NoFit;
                }

            fitOut = fitOut.combineWith(fitExpr);
            }

        return pref == TuplePref.Required
                ? fitOut.addPack()
                : fitOut;
        }

    @Override
    protected Expression validateMulti(Context ctx, TypeConstant[] atypeRequired, TuplePref pref, ErrorListener errs)
        {
        List<Expression> listExprs = exprs;
        int              cExprs    = listExprs == null ? 0 : listExprs.size();
        TypeConstant[]   aTypes    = new TypeConstant[cExprs];
        Constant[]       aVals     = null;
        boolean          fConstant = true;
        int              cRequired = atypeRequired.length;
        boolean          fPack     = false;
        if (cRequired == 1 && atypeRequired[0].isTuple() && pref != TuplePref.Required)
            {
            // it is acceptable to ask a tuple expression to be of type tuple, which results in the
            // tuple expression packing itself
            TypeConstant[] aTypeFields = atypeRequired[0].getParamTypesArray();
            TypeFit fit = testFitMulti(ctx, aTypeFields, TuplePref.Required);
            if (fit.isFit())
                {
                atypeRequired = aTypeFields;
                fPack         = true;
                }
            }

        if (cRequired > cExprs)
            {
            // we don't have enough expressions to satisfy the caller
            log(errs, Severity.ERROR, Compiler.WRONG_TYPE_ARITY, cRequired, cExprs);

            // pretend that we can satisfy the caller (so that this doesn't prevent the compilation
            // from continuing)
            aTypes    = new TypeConstant[cRequired];
            aVals     = new Constant[cRequired];
            for (int i = cExprs; i < cRequired; ++i)
                {
                TypeConstant typeField = atypeRequired[i];
                aTypes[i] = typeField;
                aVals [i] = generateFakeConstant(typeField);
                }
            }

        boolean fHalted = false;
        TypeFit fit     = TypeFit.Fit;
        for (int i = 0; i < cExprs; ++i)
            {
            TypeConstant typeRequired = i < cRequired
                    ? atypeRequired[i]
                    : null;

            Expression exprOrig = listExprs.get(i);
            Expression expr     = exprOrig.validate(ctx, typeRequired, TuplePref.Rejected, errs);
            if (expr == null)
                {
                fit       = TypeFit.NoFit;
                fHalted   = true;
                fConstant = false;
                }
            else if (expr != exprOrig)
                {
                listExprs.set(i, expr);
                }

            // collect the fit of the validated expression
            if (fit.isFit())
                {
                TypeFit fitExpr = expr.getTypeFit();
                if (fitExpr.isFit())
                    {
                    fit = fit.combineWith(fitExpr);
                    }
                else
                    {
                    fit = TypeFit.NoFit;
                    }
                }

            // collect the type of the validated expression
            aTypes[i] = expr == null
                    ? typeRequired          // pretend we were able to get the requested type
                    : expr.getType();

            // collect the constant value of the validated expression
            if (fConstant)
                {
                if (expr == null || expr.isConstant())
                    {
                    if (aVals == null)
                        {
                        aVals = new Constant[cExprs];
                        }

                    aVals[i] = expr == null
                            ? generateFakeConstant(typeRequired)        // pretend it's a constant
                            : expr.toConstant();
                    }
                else
                    {
                    fConstant = false;
                    aVals     = null;
                    }
                }
            }

        finishValidations(fit, aTypes, fConstant ? aVals : null);

        return fHalted  ? null
                : fPack ? new PackExpression(this)
                : this;
        }

    @Override
    public boolean isAborting()
        {
        for (Expression expr : exprs)
            {
            if (expr.isAborting())
                {
                return true;
                }
            }
        return false;
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
    public Argument[] generateArguments(Code code, boolean fPack, ErrorListener errs)
        {
        ConstantPool pool = pool();
        TypeConstant type = fPack
                ? pool.ensureParameterizedTypeConstant(pool.typeTuple(), getTypes())
                : null;

        if (isConstant())
            {
            if (!fPack)
                {
                return toConstants();
                }

            return new Constant[] {pool.ensureTupleConstant(type, toConstants())};
            }

        List<Expression> listExprs = exprs;
        int              cExprs    = listExprs == null ? 0 : listExprs.size();
        Argument[]       aArgs     = new Argument[cExprs];
        for (int i = 0; i < cExprs; ++i)
            {
            aArgs[i] = exprs.get(i).generateArgument(code, false, errs);
            }

        if (!fPack)
            {
            return aArgs;
            }

        // generate the tuple itself, and return it as an argument
        code.add(new Var_T(type, aArgs));
        return new Argument[] {code.lastRegister()};
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append('(');

        if (exprs != null)
            {
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

    protected TypeExpression   type;
    protected List<Expression> exprs;
    protected long             m_lStartPos;
    protected long             m_lEndPos;

    private static final Field[] CHILD_FIELDS =
            fieldsForNames(TupleExpression.class, "type", "exprs");
    }
