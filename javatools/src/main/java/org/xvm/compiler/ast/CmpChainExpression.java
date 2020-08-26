package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.xvm.asm.Argument;
import org.xvm.asm.Component;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.GenericTypeResolver;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.OpCondJump;
import org.xvm.asm.OpTest;

import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.constants.TypeInfo;
import org.xvm.asm.op.IsEq;
import org.xvm.asm.op.IsGt;
import org.xvm.asm.op.IsGte;
import org.xvm.asm.op.IsLt;
import org.xvm.asm.op.IsLte;
import org.xvm.asm.op.IsNotEq;
import org.xvm.asm.op.IsNull;
import org.xvm.asm.op.Jump;
import org.xvm.asm.op.JumpEq;
import org.xvm.asm.op.JumpGt;
import org.xvm.asm.op.JumpGte;
import org.xvm.asm.op.JumpLt;
import org.xvm.asm.op.JumpLte;
import org.xvm.asm.op.JumpNotEq;
import org.xvm.asm.op.JumpNotNull;
import org.xvm.asm.op.JumpNull;
import org.xvm.asm.op.Label;
import org.xvm.asm.op.Move;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;

import org.xvm.util.Handy;
import org.xvm.util.ListSet;
import org.xvm.util.Severity;


/**
 * Comparison over a chain of expressions.
 *
 * <pre><code>
 *     if (a == b == c) {...}
 *     if (a != b != c) {...}
 *     if (a < b <= c) {...}
 *     if (a > b >= c) {...}
 * </code></pre>
 *
 * @see CmpExpression
 */
public class CmpChainExpression
        extends Expression
    {
    // ----- constructors --------------------------------------------------------------------------

    public CmpChainExpression(Expression[] expressions, Token[] operators)
        {
        this.expressions = expressions;
        this.operators   = operators;
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return true iff the expression uses a type composition's equals() function, or false iff the
     *         expression uses a type composition's compare() function
     */
    public boolean usesEquals()
        {
        Id id = operators[0].getId();
        return id == Id.COMP_EQ | id == Id.COMP_NEQ;
        }

    public boolean isAscending()
        {
        Id id = operators[0].getId();
        return id == Id.COMP_LT | id == Id.COMP_LTEQ;
        }

    @Override
    public long getStartPosition()
        {
        return expressions[0].getStartPosition();
        }

    @Override
    public long getEndPosition()
        {
        return expressions[expressions.length-1].getEndPosition();
        }

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }


    // ----- compilation ---------------------------------------------------------------------------

    @Override
    public TypeConstant getImplicitType(Context ctx)
        {
        return ctx.pool().typeBoolean();
        }

    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
        boolean fValid = true;

        // find the common type across all of the expressions, hopefully before having to validate
        // any of the expressions
        ConstantPool pool        = ctx.pool();
        boolean      fOrdered    = !usesEquals();
        Expression[] aExprs      = expressions;
        int          cExprs      = aExprs.length;
        for (int cValidate = 0; cValidate <= cExprs; ++cValidate)
            {
            if (cValidate > 0)
                {
                Expression exprOld = aExprs[0];
                Expression exprNew = exprOld.validate(ctx, fOrdered ? pool.typeOrderable() : null, errs);
                }
            }

        TypeConstant typeRequest = chooseCommonType(ctx, aExprs, fOrdered, false, new HashSet<>(), errs);
        boolean      fForceFirst = typeRequest == null;
        if (fForceFirst)
            {
            Expression exprNew = aExprs[0].validate(ctx, fOrdered ? pool.typeOrderable() : null, errs);
            if (exprNew == null)
                {
                fValid = false;
                }
            else
                {
                TypeConstant typeForced = exprNew.getType();
                typeRequest = chooseCommonType(ctx, aExprs, fOrdered, false, new HashSet<>(), errs);
                }
            }

        // allow the expressions to resolve names based on the requested type
        boolean fInfer = typeRequest != null;
        if (fInfer)
            {
            ctx = ctx.enterInferring(typeRequest);
            }
        else
            {
            typeRequest = fOrdered ? pool.typeOrderable() : null;
            }

        for (int i = fForceFirst ? 1 : 0; i < cExprs; ++i)
            {
            Expression exprNew = aExprs[i].validate(ctx, typeRequest, errs);
            if (exprNew == null)
                {
                fValid = false;
                }
            else
                {
                aExprs[i] = exprNew;
                }
            }

        if (fInfer)
            {
            ctx = ctx.exit();
            }
        else
            {
            typeRequest = chooseCommonType(ctx, aExprs, fOrdered, true, new HashSet<>(), errs);
            CheckConversions: if (typeRequest != null)
                {
                // test if we can convert all of the expressions to the decided-upon type
                MethodConstant[] aConvMethod = new MethodConstant[cExprs];
                for (int i = 0; i < cExprs; ++i)
                    {
                    TypeConstant typePre = aExprs[i].getType();
                    if (!typePre.isA(typeRequest))
                        {
                        MethodConstant method = typePre.getConverterTo(typeRequest);
                        if (method == null)
                            {
                            typeRequest = null;
                            break CheckConversions;
                            }
                        else
                            {
                            aConvMethod[i] = method;
                            }
                        }
                    }

                for (int i = 0; i < cExprs; ++i)
                    {
                    MethodConstant method = aConvMethod[i];
                    if (method != null)
                        {
                        aExprs[i] = new ConvertExpression(aExprs[i], 0, method, errs);
                        }
                    }
                }

            if (typeRequest == null)
                {
                if (fOrdered)
                    {
                    // TODO log error
                    fValid = false;
                    }
                else
                    {
                    // for equality, just use Object
                    typeRequest = pool.typeObject();
                    }
                }
            }

        // store the resulting common type to compare
        m_typeCommon = typeRequest;

//                // make sure that we can compare the left value to the right value
//                TypeConstant typeCommon = chooseCommonType(type1, type2, true);
//                if (typeCommon == null)
//                    {
//                    // try to resolve the types using the current context
//                    GenericTypeResolver resolver = ctx.getFormalTypeResolver();
//                    TypeConstant        type1R   = type1.resolveGenerics(pool, resolver);
//                    TypeConstant        type2R   = type2.resolveGenerics(pool, resolver);
//
//                    typeCommon = chooseCommonType(type1R, type2R, true);
//                    }
//
//                if (typeCommon == null)
//                    {
//                    fValid = false;
//                    if (type1.equals(pool.typeNull()))
//                        {
//                        log(errs, Severity.ERROR, Compiler.EXPRESSION_NOT_NULLABLE,
//                                type2.getValueString());
//                        }
//                    else if (type2.equals(pool.typeNull()))
//                        {
//                        log(errs, Severity.ERROR, Compiler.EXPRESSION_NOT_NULLABLE,
//                                type1.getValueString());
//                        }
//                    else
//                        {
//                        log(errs, Severity.ERROR, Compiler.TYPES_NOT_COMPARABLE,
//                                type1.getValueString(), type2.getValueString());
//                        }
//                    }
//                else
//                    {
//                    m_typeCommon = typeCommon;
//                    }

        // for this to be a constant expression, either all sub-expressions are constant and the
        // result is calculated from that, or left-to-right, enough expressions are constant that
        // the result can be proven to always be false; for "==", see if there is a constant
        // expression that everything else can be compared to
        Constant constVal = null;
        if (fValid)
            {
            Constant     constPrev  = null;
            boolean      fEquality  = operators[0].getId() == Id.COMP_EQ;
            Constant     FALSE      = pool.valFalse();
            boolean fAllConst = true;
            Loop: for (int i = 0; i < cExprs; ++i)
                {
                Expression expr = aExprs[i];
                if (expr.isConstant())
                    {
                    if (fAllConst)
                        {
                        if (i > 0)
                            {
                            try
                                {
                                constVal = constPrev.apply(operators[i-1].getId(), expr.toConstant());
                                }
                            catch (RuntimeException e)
                                {
                                constVal  = null;
                                fAllConst = false;
                                }

                            if (constVal != null && constVal.equals(FALSE))
                                {
                                break Loop;
                                }
                            }
                        }

                    if (fEquality && m_constEq == null)
                        {
                        // store off the constant to compare to
                        m_constEq = expr.toConstant();
                        }
                    }
                else
                    {
                    fAllConst = false;
                    }
                }

            if (m_constEq != null && m_constEq.equals(pool.valNull()))
                {
                fValid = checkNullComparison(ctx, aExprs, errs);
                }
            }

        return finishValidation(ctx, typeRequired, getImplicitType(ctx),
                fValid ? TypeFit.Fit : TypeFit.NoFit, constVal, errs);
        }

    @Override
    public void generateAssignment(Context ctx, Code code, Assignable LVal, ErrorListener errs)
        {
        if (!LVal.isLocalArgument())
            {
            super.generateAssignment(ctx, code, LVal, errs);
            return;
            }

        Expression[] aExpr      = expressions;
        Constant     constVal   = m_constEq;
        TypeConstant typeCmp    = m_typeCommon;
        Argument     argResult  = LVal.getLocalArgument();
        Label        labelFalse = new Label("not_eq");
        Label        labelEnd   = new Label("end_eq");
        ConstantPool pool       = ctx.pool();
        if (constVal == null)
            {
            // everything has to get compared, left to right
            Token[]  aTokOp  = operators;
            Argument argPrev = null;
            for (int iCmp = 0, iLastCmp = aTokOp.length - 1; iCmp <= iLastCmp; ++iCmp)
                {
                // evaluate the sub-expressions
                boolean  fLast = iCmp == iLastCmp;
                Argument arg1  = argPrev == null
                        ? aExpr[iCmp].generateArgument(ctx, code, true, true, errs)
                        : argPrev;
                Argument arg2  = aExpr[iCmp+1].generateArgument(ctx, code, fLast, fLast, errs);

                if (fLast)
                    {
                    OpTest opTest;
                    switch (operators[iCmp].getId())
                        {
                        case COMP_EQ:
                            opTest = new IsEq(arg1, arg2, argResult);
                            break;
                        case COMP_NEQ:
                            opTest = new IsNotEq(arg1, arg2, argResult);
                            break;
                        case COMP_LT:
                            opTest = new IsLt(arg1, arg2, argResult);
                            break;
                        case COMP_GT:
                            opTest = new IsGt(arg1, arg2, argResult);
                            break;
                        case COMP_LTEQ:
                            opTest = new IsLte(arg1, arg2, argResult);
                            break;
                        case COMP_GTEQ:
                            opTest = new IsGte(arg1, arg2, argResult);
                            break;
                        default:
                            throw new IllegalStateException();
                        }

                    opTest.setCommonType(typeCmp);
                    code.add(opTest);
                    }
                else
                    {
                    OpCondJump opCondJump;
                    switch (operators[iCmp].getId())
                        {
                        case COMP_EQ:
                            opCondJump = new JumpNotEq(arg1, arg2, labelFalse);
                            break;
                        case COMP_NEQ:
                            opCondJump = new JumpEq(arg1, arg2, labelFalse);
                            break;
                        case COMP_LT:
                            opCondJump = new JumpGte(arg1, arg2, labelFalse);
                            break;
                        case COMP_GT:
                            opCondJump = new JumpLte(arg1, arg2, labelFalse);
                            break;
                        case COMP_LTEQ:
                            opCondJump = new JumpGt(arg1, arg2, labelFalse);
                            break;
                        case COMP_GTEQ:
                            opCondJump = new JumpLt(arg1, arg2, labelFalse);
                            break;
                        default:
                            throw new IllegalStateException();
                        }

                    opCondJump.setCommonType(typeCmp);
                    code.add(opCondJump);
                    }

                // each time through the loop after the first time, the "left" expression to compare
                // is the same value that was computed/loaded for the "right" expression on the
                // previous loop
                argPrev = arg2;
                }
            }
        else
            {
            // optimize equality comparisons to a constant, especially to null
            boolean fNull = constVal.equals(pool.valNull());
            for (int iExpr = 0, iLastExpr = aExpr.length - 1; iExpr <= iLastExpr; ++iExpr)
                {
                Expression expr  = aExpr[iExpr];
                Argument   arg   = expr.generateArgument(ctx, code, true, true, errs);
                if (iExpr == iLastExpr)
                    {
                    OpTest opTest = fNull
                            ? new IsNull(arg, argResult)
                            : new IsEq(arg, constVal, argResult);
                    opTest.setCommonType(typeCmp);
                    code.add(opTest);
                    }
                else
                    {
                    OpCondJump opCondJump = fNull
                            ? new JumpNotNull(arg, labelFalse)
                            : new JumpNotEq(arg, constVal, labelFalse);
                    opCondJump.setCommonType(typeCmp);
                    code.add(opCondJump);
                    }
                }
            }

        code.add(new Jump(labelEnd))
            .add(labelFalse)
            .add(new Move(pool.valFalse(), argResult))
            .add(labelEnd);
        }

    @Override
    public void generateConditionalJump(
            Context ctx, Code code, Label label, boolean fWhenTrue, ErrorListener errs)
        {
        // constant expressions can be handled by the default implementation
        if (isConstant())
            {
            super.generateConditionalJump(ctx, code, label, fWhenTrue, errs);
            return;
            }

        // optimize equality comparisons to a constant, especially to null
        Expression[] aExpr    = expressions;
        Constant     constVal = m_constEq;
        TypeConstant typeCmp  = m_typeCommon;
        if (constVal != null)
            {
            boolean fNull = constVal.equals(pool().valNull());
// TODO
            for (Expression expr : aExpr)
                {
                Argument   arg = expr.generateArgument(ctx, code, true, true, errs);
                OpCondJump op  = fNull
                        ? new JumpNull(arg, label)
                        : new JumpEq(arg, constVal, label);
                op.setCommonType(typeCmp);
                code.add(op);
                }
            return;
            }

        // otherwise, everything has to get compared, left to right
        Token[]  aTokOp  = operators;
        Argument argPrev = null;
        for (int iCmp = 0, iLastCmp = aTokOp.length - 1; iCmp <= iLastCmp; ++iCmp)
            {
            // evaluate the sub-expressions
            boolean  fLast = iCmp == iLastCmp;
            Argument arg1  = argPrev == null
                    ? aExpr[iCmp].generateArgument(ctx, code, true, true, errs)
                    : argPrev;
            Argument arg2  = aExpr[iCmp+1].generateArgument(ctx, code, fLast, fLast, errs);

            // generate the op that combines the two sub-expressions
            OpCondJump op;
            switch (operators[iCmp].getId())
                {
                case COMP_EQ:
                    op = fWhenTrue
                        ? new JumpEq   (arg1, arg2, label)
                        : new JumpNotEq(arg1, arg2, label);
                    break;

                case COMP_NEQ:
                    op = fWhenTrue
                        ? new JumpNotEq(arg1, arg2, label)
                        : new JumpEq   (arg1, arg2, label);
                    break;

                case COMP_LT:
                    op = fWhenTrue
                        ? new JumpLt(arg1, arg2, label)
                        : new JumpGte(arg1, arg2, label);
                    break;

                case COMP_GT:
                    op = fWhenTrue
                        ? new JumpGt(arg1, arg2, label)
                        : new JumpLte(arg1, arg2, label);
                    break;

                case COMP_LTEQ:
                    op = fWhenTrue
                        ? new JumpLte(arg1, arg2, label)
                        : new JumpGt(arg1, arg2, label);
                    break;

                case COMP_GTEQ:
                    op = fWhenTrue
                        ? new JumpGte(arg1, arg2, label)
                        : new JumpLt(arg1, arg2, label);
                    break;

                default:
                    throw new IllegalStateException();
                }

            op.setCommonType(typeCmp);
            code.add(op);

            // each time through the loop after the first time, the "left" expression to compare is
            // the same value that was computed/loaded for the "right" expression on the previous
            // loop
            argPrev = arg2;
            }
        }


    // ----- compilation ---------------------------------------------------------------------------

    @Override
    public boolean isShortCircuiting()
        {
        for (Expression expr : expressions)
            {
            if (expr.isShortCircuiting())
                {
                return true;
                }
            }
        return false;
        }

    @Override
    public boolean isCompletable()
        {
        return expressions[0].isCompletable() && expressions[1].isCompletable();
        }


    // ----- internal ------------------------------------------------------------------------------

    /**
     * TODO doc
     *
     * @param ctx
     * @param aExpr
     * @param fOrdered
     * @param fCheck
     * @param setTried
     * @param errs
     *
     * @return the selected common type, or null
     */
    protected static TypeConstant chooseCommonType(Context           ctx,
                                                   Expression[]      aExpr,
                                                   boolean           fOrdered,
                                                   boolean           fCheck,
                                                   Set<TypeConstant> setTried,
                                                   ErrorListener     errs)
        {
        // first, try the implicit types
        for (Expression expr : aExpr)
            {
            TypeConstant type = inferCommonType(expr.getImplicitType(ctx),
                    ctx, aExpr, fOrdered, fCheck, setTried, errs);
            if (type != null)
                {
                return type;
                }
            }

        // next, try the conversions from the implicit types
        for (Expression expr : aExpr)
            {
            TypeConstant typeOrig = expr.getImplicitType(ctx);
            if (typeOrig != null)
                {
                for (MethodInfo method : typeOrig.ensureTypeInfo(errs).getAutoMethodInfos())
                    {
                    List<TypeConstant> listRets = method.getSignature().getReturns();
                    if (!listRets.isEmpty())
                        {
                        TypeConstant type = inferCommonType(listRets.get(0),
                                ctx, aExpr, fOrdered, fCheck, setTried, errs);
                        if (type != null)
                            {
                            return type;
                            }
                        }
                    }
                }
            }

        return null;
        }

    /**
     * TODO
     *
     * @param type
     * @param ctx
     * @param aExpr
     * @param fOrdered
     * @param fCheck
     * @param setTried
     * @param errs
     *
     * @return
     */
    protected static TypeConstant inferCommonType(TypeConstant      type,
                                                  Context           ctx,
                                                  Expression[]      aExpr,
                                                  boolean           fOrdered,
                                                  boolean           fCheck,
                                                  Set<TypeConstant> setTried,
                                                  ErrorListener     errs)
        {
        if (type == null || setTried.contains(type))
            {
            return null;
            }

        setTried.add(type);

        TypeInfo info = type.ensureTypeInfo(errs);
        if (info.getFormat() == Component.Format.ENUMVALUE)
            {
            type = info.getExtends();
            setTried.add(type);
            }

        if (fCheck)
            {
            if (!(fOrdered ? type.supportsCompare(type, false) : type.supportsEquals(type, false)))
                {
                return null;
                }
            }

        TypeConstant typeNotNull = type.removeNullable();
        if (!typeNotNull.equals(type) && !setTried.contains(typeNotNull))
            {
            setTried.add(typeNotNull);
            if (testCommonType(typeNotNull, ctx, aExpr))
                {
                return typeNotNull;
                }
            }

        if (testCommonType(type, ctx, aExpr))
            {
            return type;
            }

        TypeConstant typeNullable = type.ensureNullable();
        if (!typeNullable.equals(type) && !setTried.contains(typeNullable))
            {
            setTried.add(typeNullable);
            if (testCommonType(typeNullable, ctx, aExpr))
                {
                return typeNullable;
                }
            }

        TypeConstant typeRef = ctx.pool().typeRef();
        if (type.isA(typeRef) && !setTried.contains(typeRef))
            {
            setTried.add(typeRef);
            if (testCommonType(typeRef, ctx, aExpr))
                {
                return typeRef;
                }
            }

        // TODO formal types

        return null;
        }

    /**
     * TODO
     *
     * @param type
     * @param ctx
     * @param aExpr
     *
     * @return
     */
    protected static boolean testCommonType(TypeConstant      type,
                                            Context           ctx,
                                            Expression[]      aExpr)
        {
        boolean fResult = true;
        ctx = ctx.enterInferring(type);
        for (Expression expr : aExpr)
            {
            if (!expr.testFit(ctx, type, null).isFit())
                {
                fResult = false;
                break;
                }
            }
        ctx.exit();
        return fResult;
        }

    boolean checkNullComparison(Context ctx, Expression[] aExprs, ErrorListener errs)
        {
        TypeConstant typeNull = pool().typeNull();
        for (Expression expr : aExprs)
            {
            TypeConstant type = expr.getType();
            if (!type.isNullable() && !typeNull.isA(type.resolveConstraints()))
                {
                log(errs, Severity.ERROR, Compiler.EXPRESSION_NOT_NULLABLE, type.getValueString());
                return false;
                }
            }

// TODO GG
//        switch (operator.getId())
//            {
//            case COMP_EQ:
//                typeTrue  = typeNull;
//                typeFalse = typeTarget.removeNullable();
//                break;
//
//            case COMP_NEQ:
//                typeTrue  = typeTarget.removeNullable();
//                typeFalse = typeNull;
//                break;
//            }
//
//        exprTarget.narrowType(ctx, Branch.WhenTrue,  typeTrue);
//        exprTarget.narrowType(ctx, Branch.WhenFalse, typeFalse);

        return true;
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuffer sb = new StringBuffer();
        sb.append(expressions[0]);
        for (int i = 0, c = operators.length; i < c; ++i)
            {
            sb.append(' ')
              .append(operators[i].getId().TEXT)
              .append(' ')
              .append(expressions[i+1]);
            }
        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Expression[] expressions;
    protected Token[]      operators;

    /**
     * The common type used for the comparison.
     */
    private TypeConstant m_typeCommon;

    /**
     * The constant value that all other expressions are compared to for equality; often
     * {@link ConstantPool#valNull()}.
     */
    private Constant m_constEq;

    private static final Field[] CHILD_FIELDS = fieldsForNames(CmpChainExpression.class, "expressions");
    }
