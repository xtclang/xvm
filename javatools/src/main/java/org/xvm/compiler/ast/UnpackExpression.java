package org.xvm.compiler.ast;


import java.util.List;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.Constant.Format;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.constants.ArrayConstant;
import org.xvm.asm.constants.TypeConstant;


/**
 * A tuple un-packing expression. This unpacks the values from the sub-expression tuple.
 */
public class UnpackExpression
        extends SyntheticExpression
    {
    // ----- constructors --------------------------------------------------------------------------

    public UnpackExpression(TupleExpression exprTuple, ErrorListener errs)
        {
        super(exprTuple);

        if (exprTuple.isValidated())
            {
            adoptValidation(null, exprTuple, errs);
            }
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
        return isValidated()
                ? getTypes()
                : expr.getImplicitType(ctx).getParamTypesArray();
        }

    @Override
    protected Expression validateMulti(Context ctx, TypeConstant[] atypeRequired, ErrorListener errs)
        {
        TypeConstant typeTuple = pool().ensureTupleType(atypeRequired);

        Expression exprOld = expr;
        Expression exprNew = exprOld.validate(ctx, typeTuple, errs);

        return exprNew == null
                ? null
                : adoptValidation(ctx, expr = exprNew, errs);
        }


    @Override
    public void generateVoid(Context ctx, Code code, ErrorListener errs)
        {
        expr.generateVoid(ctx, code, errs);
        }

    @Override
    public Argument[] generateArguments(Context ctx, Code code, boolean fLocalPropOk,
                                        boolean fUsedOnce, ErrorListener errs)
        {
        if (isConstant())
            {
            return toConstants();
            }

        List<Expression> exprs  = ((TupleExpression) expr).getExpressions();
        int              cExprs = exprs.size();
        Argument[]       aArgs  = new Argument[cExprs];
        for (int i = 0; i < cExprs; ++i)
            {
            aArgs[i] = exprs.get(i).generateArgument(ctx, code, false, false, errs);
            }

        return aArgs;
        }


    // ----- helpers ------------------------------------------------------------------

    /**
     * Adopt the type information from a validated expression.
     *
     * @param ctx        the compiler context
     * @param exprTuple  the validated expression that yields a Tuple type
     * @param errs       the error listener
     */
    protected Expression adoptValidation(Context ctx, Expression exprTuple, ErrorListener errs)
        {
        TypeConstant typeTuple = exprTuple.getType();
        assert typeTuple.isTuple() && typeTuple.isParamsSpecified();

        TypeConstant[] atypeField = typeTuple.getParamTypesArray();
        Constant[]     aconstVal  = null;

        if (exprTuple.isConstant())
            {
            Constant constTuple = exprTuple.toConstant();
            assert constTuple.getFormat() == Format.Tuple;
            aconstVal = ((ArrayConstant) constTuple).getValue();
            }

        return finishValidations(ctx, null, atypeField, expr.getTypeFit().addUnpack(), aconstVal, errs);
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        return "Unpacked:" + getUnderlyingExpression().toString();
        }
    }
