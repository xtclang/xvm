package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.Arrays;
import java.util.List;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorList;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Argument;

import org.xvm.asm.constants.ArrayConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.Var_T;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Compiler.Stage;

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

    /**
     * Constructs a "synthetic" Tuple Expression out of multiple expressions.
     *
     * @param aExprs  an array of at least one expression to turn into a tuple
     */
    TupleExpression(Expression[] aExprs, ErrorList errs)
        {
        int cExprs = aExprs.length;
        assert cExprs > 0;

        Expression expr0 = aExprs[0];

        this.type        = null;
        this.exprs       = Arrays.asList(aExprs);
        this.m_lStartPos = expr0.getStartPosition();
        this.m_lEndPos   = aExprs[cExprs-1].getEndPosition();

        expr0.getParent().introduceParentage(this);
        Stage          stage      = expr0.getStage();
        boolean        fValidated = expr0.isValidated();
        TypeConstant[] aTypes     = fValidated ? new TypeConstant[cExprs] : null;
        boolean        fConstant  = fValidated && expr0.isConstant();
        Constant[]     aVals      = fConstant ? new Constant[cExprs] : null;
        for (int i = 0; i < cExprs; ++i)
            {
            Expression exprChild = aExprs[i];
            assert exprChild.getStage() == stage;
            assert exprChild.isValidated() == fValidated;

            this.introduceParentage(exprChild);

            if (fValidated)
                {
                aTypes[i] = exprChild.getType();
                if (fConstant)
                    {
                    if (exprChild.isConstant())
                        {
                        aVals[i] = exprChild.toConstant();
                        }
                    else
                        {
                        aVals     = null;
                        fConstant = false;
                        }
                    }
                }
            }

        setStage(stage);
        if (fValidated)
            {
            finishValidations(null, aTypes, TypeFit.Fit, aVals, errs);
            }
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
     * @return a list of the expressions making up the tuple value
     */
    public List<Expression> getExpressions()
        {
        return exprs;
        }

    /**
     * @return an array of the expressions making up the tuple value
     */
    public Expression[] getExpressionArray()
        {
        List<Expression> list   = exprs;
        int              cExprs = list.size();
        Expression[]     aExpr  = new Expression[cExprs];
        return list.toArray(aExpr);
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
    protected Expression[] unpackedExpressions(ErrorListener errs)
        {
        return getExpressionArray();
        }

    @Override
    public TypeConstant getImplicitType(Context ctx)
        {
        ConstantPool pool      = pool();
        TypeConstant typeTuple = type == null ? pool.typeTuple() : type.ensureTypeConstant();
        if (!typeTuple.isTuple())
            {
            // let someone else log an error later, e.g. during validation, if the specified type
            // can't be achieved
            return typeTuple;
            }

        List<Expression> listFieldExprs = exprs;
        int              cFields        = listFieldExprs.size();
        TypeConstant[]   atypeFields    = new TypeConstant[cFields];
        if (typeTuple.isParamsSpecified())
            {
            TypeConstant[] atypeSpecified = typeTuple.getParamTypesArray();
            int            cSpecified     = atypeSpecified.length;
            System.arraycopy(atypeSpecified, 0, atypeFields, 0, Math.min(cFields, cSpecified));
            }

        // the type derives from the expressions in the tuple
        for (int i = 0; i < cFields; ++i)
            {
            TypeConstant typeSpecified = atypeFields[i];
            TypeConstant typeImplicit  = listFieldExprs.get(i).getImplicitType(ctx);
            if (typeSpecified != null && typeImplicit != null)
                {
                // assume that "isA" success indicates a narrower type is known implicitly
                // assume that "isA" failure indicates a pending conversion to a specified type
                atypeFields[i] = typeImplicit.isA(typeSpecified)
                        ? typeImplicit
                        : typeSpecified;
                }
            else if (typeImplicit != null)
                {
                atypeFields[i] = typeImplicit;
                }
            else if (typeSpecified == null)
                {
                atypeFields[i] = pool.typeObject();
                }
            }

        return typeTuple.adoptParameters(atypeFields);
        }

    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
        TypeFit          fit            = TypeFit.Fit;
        ConstantPool     pool           = pool();
        List<Expression> listFieldExprs = exprs;
        int              cFields        = listFieldExprs == null ? 0 : listFieldExprs.size();
        TypeConstant[]   aFieldTypes    = new TypeConstant[cFields];
        Constant[]       aFieldVals     = null;
        boolean          fConstant      = true;
        TypeConstant[]   aReqTypes      = TypeConstant.NO_TYPES;
        int              cReqTypes      = 0;
        TypeConstant[]   aSpecTypes     = TypeConstant.NO_TYPES;
        int              cSpecTypes     = 0;
        TypeConstant     typeSpecified  = type == null ? null : type.ensureTypeConstant();
        if (typeSpecified != null)
            {
            // the specified type must be a tuple, since a tuple does not have any @Auto conversions
            // REVIEW many more checks, e.g. generally should not be relational, immutable actually means something, what annotations are allowed, etc.
            if (typeSpecified.isTuple())
                {
                // the specified tuple type may have any of the field types specified as well
                if (typeSpecified.isParamsSpecified())
                    {
                    aSpecTypes = typeSpecified.getParamTypesArray();
                    cSpecTypes = aSpecTypes.length;

                    // can't have more field types specified than we have fields
                    if (cSpecTypes > cFields)
                        {
                        // log an error and ignore the additional specified fields
                        type.log(errs, Severity.ERROR, Compiler.TUPLE_TYPE_WRONG_ARITY,
                                cFields, cSpecTypes);
                        }
                    }
                }
            else
                {
                // log an error and ignore the specified type
                type.log(errs, Severity.ERROR, Compiler.WRONG_TYPE,
                        pool.typeTuple(), typeSpecified);
                }
            }

        if (typeRequired != null && typeRequired.isTuple() && typeRequired.isParamsSpecified())
            {
            // the required type must be a tuple, or a tuple must be assignable to the required
            // type, but most of that will be checked during finishValidation(); for now, just
            // get the required field types to use while validating sub-expressions
            aReqTypes = typeRequired.getParamTypesArray();
            cReqTypes = aReqTypes.length;

            // can't have more field types required than we have fields
            if (cReqTypes > cFields)
                {
                // log an error and ignore the additional required fields
                log(errs, Severity.ERROR, Compiler.TUPLE_TYPE_WRONG_ARITY,
                        cReqTypes, cFields);
                fit = TypeFit.NoFit;
                }
            }

        boolean fHalted = false;
        TypeFit fit     = TypeFit.Fit;
        for (int i = 0; i < cFields; ++i)
            {
            TypeConstant typeRequired = i < cReqTypes
                    ? atypeRequired[i]
                    : null;

            Expression exprOrig = listFieldExprs.get(i);
            Expression expr     = exprOrig.validate(ctx, typeRequired, errs);
            if (expr == null)
                {
                fit       = TypeFit.NoFit;
                fHalted   = true;
                fConstant = false;
                }
            else if (expr != exprOrig)
                {
                listFieldExprs.set(i, expr);
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
            aFieldTypes[i] = expr == null
                    ? typeRequired          // pretend we were able to get the requested type
                    : expr.getType();

            // collect the constant value of the validated expression
            if (fConstant)
                {
                if (expr == null || expr.isConstant())
                    {
                    if (aFieldVals == null)
                        {
                        aFieldVals = new Constant[cFields];
                        }

                    aFieldVals[i] = expr == null
                            ? generateFakeConstant(typeRequired)        // pretend it's a constant
                            : expr.toConstant();
                    }
                else
                    {
                    fConstant = false;
                    aFieldVals     = null;
                    }
                }
            }

        TypeConstant  typeResult = pool.typeTuple(); // TODO use typeSpecified ?
        ArrayConstant constVal   = fConstant ? pool.ensureTupleConstant(typeResult, aFieldVals) : null;
        return finishValidation(typeRequired, typeResult, fit, constVal, errs);
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
    public Argument[] generateArguments(Code code, boolean fLocalPropOk, boolean fUsedOnce,
            ErrorListener errs)
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
            aArgs[i] = exprs.get(i).generateArgument(code, false, false, errs);
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

    private static final Field[] CHILD_FIELDS = fieldsForNames(TupleExpression.class, "type", "exprs");
    }
