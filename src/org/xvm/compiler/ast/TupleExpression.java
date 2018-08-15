package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.Arrays;
import java.util.Collections;
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
        this.exprs       = exprs == null ? Collections.EMPTY_LIST : exprs;
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
        List<Expression> list = exprs;
        return list.toArray(new Expression[list.size()]);
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
    protected Expression validate(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
        TypeFit          fit            = TypeFit.Fit;
        ConstantPool     pool           = pool();
        List<Expression> listFieldExprs = exprs;
        int              cFields        = listFieldExprs == null ? 0 : listFieldExprs.size();
        TypeConstant     typeResult     = null;
        TypeConstant[]   aSpecTypes     = TypeConstant.NO_TYPES;
        int              cSpecTypes     = 0;
        TypeConstant[]   aReqTypes      = TypeConstant.NO_TYPES;
        int              cReqTypes      = 0;
        if (type != null)
            {
            // the specified type must be a tuple, since a tuple does not have any @Auto conversions
            // REVIEW many more checks, e.g. generally should not be relational, immutable actually means something, what annotations are allowed, etc.
            TypeConstant typeSpecified = type.ensureTypeConstant();
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

        int            cMaxTypes   = Math.max(cFields, Math.max(cSpecTypes, cReqTypes));
        TypeConstant[] aFieldTypes = new TypeConstant[cMaxTypes];
        Constant[]     aFieldVals  = null;
        boolean        fHalted     = false;
        for (int i = 0; i < cMaxTypes; ++i)
            {
            TypeConstant typeSpec  = i < cSpecTypes ? aSpecTypes[i] : null;
            TypeConstant typeReq   = i < cReqTypes  ? aReqTypes[i]  : typeSpec;
            TypeConstant typeField = null;
            Expression   exprOld   = i < cFields ? listFieldExprs.get(i) : null;
            if (exprOld != null)
                {
                Expression exprNew = exprOld.validate(ctx, typeReq, errs);
                if (exprNew == null)
                    {
                    fit     = TypeFit.NoFit;
                    fHalted = true;
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

        typeResult = (typeResult == null ? pool.typeTuple() : typeResult).adoptParameters(pool, aFieldTypes);
        ArrayConstant constVal   = aFieldVals == null ? null : pool.ensureTupleConstant(typeResult, aFieldVals);
        Expression    exprResult = finishValidation(typeRequired, typeResult, fit, constVal, errs);
        return fHalted ? null : exprResult;
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
    public void generateVoid(Context ctx, Code code, ErrorListener errs)
        {
        for (Expression expr : exprs)
            {
            expr.generateVoid(ctx, code, errs);
            }
        }

    @Override
    public Argument generateArgument(
            Context ctx, Code code, boolean fLocalPropOk, boolean fUsedOnce, ErrorListener errs)
        {
        if (isConstant())
            {
            return toConstant();
            }

        int        cExprs = exprs.size();
        Argument[] aArgs  = new Argument[cExprs];
        for (int i = 0; i < cExprs; ++i)
            {
            aArgs[i] = exprs.get(i).generateArgument(ctx, code, false, false, errs);
            }

        // generate the tuple itself, and return it as an argument
        code.add(new Var_T(getType(), aArgs));
        return code.lastRegister();
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
