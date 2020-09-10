package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Argument;

import org.xvm.asm.constants.ArrayConstant;
import org.xvm.asm.constants.IntersectionTypeConstant;
import org.xvm.asm.constants.StringConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.Var_T;
import org.xvm.asm.op.Var_TN;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Compiler.Stage;

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
        this.exprs       = exprs == null ? Collections.EMPTY_LIST : exprs;
        this.m_lStartPos = lStartPos;
        this.m_lEndPos   = lEndPos;
        }

    /**
     * Constructs a "synthetic" Tuple Expression out of multiple expressions.
     *
     * @param aExprs  an array of at least one expression to turn into a tuple
     */
    TupleExpression(Expression[] aExprs, ErrorListener errs)
        {
        int cExprs = aExprs.length;
        assert cExprs > 0;

        Expression expr0 = aExprs[0];

        this.type        = null;
        this.exprs       = Arrays.asList(aExprs);
        this.m_lStartPos = expr0.getStartPosition();
        this.m_lEndPos   = aExprs[cExprs-1].getEndPosition();

        expr0.getParent().adopt(this);

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

            this.adopt(exprChild);

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
            finishValidations(null, null, aTypes, TypeFit.Fit, aVals, errs);
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
        return exprs.toArray(new Expression[0]);
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
    public TypeConstant getImplicitType(Context ctx)
        {
        ConstantPool pool      = pool();
        TypeConstant typeTuple = type == null ? pool.typeTuple() : type.ensureTypeConstant(ctx);
        if (typeTuple.containsUnresolved() || !typeTuple.isTuple())
            {
            // let someone else log an error later, e.g. during validation, if the specified type
            // can't be achieved
            return typeTuple;
            }

        List<Expression> listFieldExprs = exprs;
        if (listFieldExprs.isEmpty())
            {
            return pool.typeTuple();
            }

        int            cFields     = listFieldExprs.size();
        TypeConstant[] atypeFields = new TypeConstant[cFields];
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

        return typeTuple.adoptParameters(pool, atypeFields);
        }

    @Override
    public TypeFit testFitMulti(Context ctx, TypeConstant[] atypeRequired, ErrorListener errs)
        {
        TypeConstant typeTuple = getImplicitType(ctx);
        if (atypeRequired.length == 1)
            {
            TypeConstant typeRequired = atypeRequired[0];
            if (typeRequired.isTuple())
                {
                atypeRequired = typeRequired.getParamTypesArray();
                if (atypeRequired.length == 0)
                    {
                    // any tuple is assignable to an empty tuple
                    return TypeFit.Fit;
                    }
                }
            else
                {
                TypeFit fit = calcFit(ctx, typeTuple, typeRequired);
                if (fit.isFit())
                    {
                    return fit;
                    }
                // fall through
                }
            }

        return calcFitMulti(ctx, typeTuple.getParamTypesArray(), atypeRequired);
        }

    @Override
    protected Expression validateMulti(Context ctx, TypeConstant[] atypeRequired, ErrorListener errs)
        {
        ConstantPool     pool           = pool();
        List<Expression> listFieldExprs = exprs;
        int              cFields        = listFieldExprs == null ? 0 : listFieldExprs.size();
        TypeConstant     typeRequired   = null;
        TypeConstant     typeResult     = null;
        TypeConstant[]   aSpecTypes     = TypeConstant.NO_TYPES;
        int              cSpecTypes     = 0;
        TypeConstant[]   aReqTypes      = TypeConstant.NO_TYPES;
        int              cReqTypes      = 0;
        boolean          fValid         = true;
        boolean          fMultiplexing;

        if (type != null)
            {
            TypeConstant typeTupleType = pool.typeTuple().getType();

            TypeExpression exprOld = type;
            TypeExpression exprNew = (TypeExpression) exprOld.validate(ctx, typeTupleType, errs);
            if (exprNew == null)
                {
                fValid = false;
                }
            else
                {
                type = exprNew;

                // the specified type must be a tuple, since a tuple does not have any @Auto conversions
                // REVIEW many more checks, e.g. generally should not be relational, immutable actually means something, what annotations are allowed, etc.
                TypeConstant typeSpecified = exprNew.ensureTypeConstant(ctx).resolveAutoNarrowingBase();
                if (typeSpecified.isTuple())
                    {
                    // the specified tuple type may have any of the field types specified as well
                    typeResult = typeSpecified;
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
                            fValid = false;
                            }
                        }
                    }
                else
                    {
                    // log an error and ignore the specified type
                    type.log(errs, Severity.ERROR, Compiler.WRONG_TYPE,
                            pool.typeTuple(), typeSpecified);
                    fValid = false;
                    }
                }
            }

        if (atypeRequired == null || atypeRequired.length == 0)
            {
            typeRequired  = pool.typeTuple();
            fMultiplexing = true;
            }
        else if (atypeRequired.length == 1)
            {
            typeRequired = atypeRequired[0];

            // the required type must be assignable from a Tuple, but that will be checked during
            // finishValidation(); for now, just try to get the required field types to use
            // while validating sub-expressions
            if (typeRequired.isTuple())
                {
                aReqTypes = typeRequired.getParamTypesArray();
                cReqTypes = aReqTypes.length;
                }
            else if (!typeRequired.equals(pool.typeObject()))
                {
                // required type could be an intersection type. e.g. (T1 | Tuple<T2>), in which case
                // we could use an a very simple helper; otherwise use implicit types
                if (typeRequired instanceof IntersectionTypeConstant)
                    {
                    TypeConstant typeTuple = ((IntersectionTypeConstant) typeRequired).extractTuple();
                    if (typeTuple != null)
                        {
                        aReqTypes = typeTuple.getParamTypesArray();
                        cReqTypes = aReqTypes.length;
                        }
                    }
                }
            fMultiplexing = true;
            }
        else
            {
            aReqTypes     = atypeRequired;
            cReqTypes     = aReqTypes.length;
            typeRequired  = pool.ensureParameterizedTypeConstant(pool.typeTuple(), aReqTypes);
            fMultiplexing = false;
            }

        // can't have more field types required than we have fields
        if (cReqTypes > cFields)
            {
            // log an error and ignore the additional required fields
            log(errs, Severity.ERROR, Compiler.TUPLE_TYPE_WRONG_ARITY,
                    cReqTypes, cFields);
            fValid = false;
            }

        int            cMaxTypes   = Math.max(cFields, Math.max(cSpecTypes, cReqTypes));
        TypeConstant[] aFieldTypes = new TypeConstant[cMaxTypes];
        Constant[]     aFieldVals  = cMaxTypes == 0 ? Constant.NO_CONSTS : null;
        for (int i = 0; i < cMaxTypes; ++i)
            {
            TypeConstant typeSpec  = i < cSpecTypes ? aSpecTypes[i] : null;
            TypeConstant typeReq   = i < cReqTypes  ? aReqTypes[i]  : typeSpec;
            TypeConstant typeField = null;
            Expression   exprOld   = i < cFields ? listFieldExprs.get(i) : null;
            if (exprOld != null)
                {
                if (typeReq != null)
                    {
                    ctx = ctx.enterInferring(typeReq);
                    }

                Expression exprNew = exprOld.validate(ctx, typeReq, errs);

                if (typeReq != null)
                    {
                    ctx = ctx.exit();
                    }

                if (exprNew == null)
                    {
                    fValid = false;
                    }
                else
                    {
                    if (exprNew != exprOld)
                        {
                        listFieldExprs.set(i, exprNew);
                        }

                    typeField = exprNew.getType();

                    if (i == 0 || aFieldVals != null)
                        {
                        Constant constVal = exprNew.toConstant();
                        if (constVal == null)
                            {
                            aFieldVals = null;
                            }
                        else
                            {
                            if (aFieldVals == null)
                                {
                                aFieldVals = new Constant[cMaxTypes];
                                }

                            aFieldVals[i] = constVal;
                            }
                        }
                    }
                }

            if (typeField == null)
                {
                typeField = typeReq == null ? pool.typeObject() : typeReq;

                if (aFieldVals != null)
                    {
                    aFieldVals[i] = generateFakeConstant(typeField);
                    }
                }

            aFieldTypes[i] = typeField;
            }

        if (fValid)
            {
            typeResult = (typeResult == null ? pool.typeTuple() : typeResult).
                    adoptParameters(pool, aFieldTypes);

            ArrayConstant constVal = null;
            if (aFieldVals != null)
                {
                typeResult = pool.ensureImmutableTypeConstant(typeResult);
                constVal   = pool.ensureTupleConstant(typeResult, aFieldVals);
                }

            Expression exprNew = finishValidation(ctx, typeRequired, typeResult, TypeFit.Fit, constVal, errs);
            if (!fMultiplexing)
                {
                assert this == exprNew;
                exprNew = new UnpackExpression(this, errs);
                }
            return exprNew;
            }

        return null;
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
    public boolean supportsCompactInit(VariableDeclarationStatement lvalue)
        {
        return true;
        }

    @Override
    public void generateCompactInit(
            Context ctx, Code code, VariableDeclarationStatement lvalue, ErrorListener errs)
        {
        if (isConstant())
            {
            super.generateCompactInit(ctx, code, lvalue, errs);
            }
        else
            {
            StringConstant idName = pool().ensureStringConstant(lvalue.getName());

            code.add(new Var_TN(lvalue.getRegister(), idName, collectArguments(ctx, code, errs)));
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

        // generate the tuple itself, and return it as an argument
        code.add(new Var_T(getType(), collectArguments(ctx, code, errs)));
        return new Argument[] {code.lastRegister()};
        }

    /**
     * Helper method to generate an array of arguments.
     */
    private Argument[] collectArguments(Context ctx, Code code, ErrorListener errs)
        {
        List<Expression> listExprs = exprs;
        int              cArgs     = listExprs.size();
        Argument[]       aArgs     = new Argument[cArgs];

        for (int i = 0; i < cArgs; ++i)
            {
            aArgs[i] = listExprs.get(i).generateArgument(ctx, code, true, false, errs);
            }
        return aArgs;
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append('(');

        if (!exprs.isEmpty())
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
