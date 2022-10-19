package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.OpCondJump;
import org.xvm.asm.OpTest;

import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.TypeConstant;

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

    public CmpChainExpression(List<Expression> expressions, Token[] operators)
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
        return expressions.get(0).getStartPosition();
        }

    @Override
    public long getEndPosition()
        {
        return expressions.get(expressions.size() - 1).getEndPosition();
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
        ConstantPool     pool      = ctx.pool();
        boolean          fOrdered  = !usesEquals();
        List<Expression> listExprs = expressions;
        int              cExprs    = listExprs.size();

        TypeConstant typeRequest = chooseCommonType(ctx, false);
        boolean      fForceFirst = typeRequest == null;
        if (fForceFirst)
            {
            Expression exprOld = listExprs.get(0);
            Expression exprNew = exprOld.validate(ctx, fOrdered ? pool.typeOrderable() : null, errs);
            if (exprNew == null)
                {
                return null;
                }
            if (exprNew != exprOld)
                {
                listExprs.set(0, exprNew);
                }
            typeRequest = chooseCommonType(ctx, false);
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
            Expression exprOld = listExprs.get(i);
            Expression exprNew = listExprs.get(i).validate(ctx, typeRequest, errs);
            if (exprNew == null)
                {
                fValid = false;
                }
            else if (exprNew != exprOld)
                {
                listExprs.set(i, exprNew);
                }
            }

        if (fInfer)
            {
            ctx = ctx.exit();
            }
        else
            {
            typeRequest = chooseCommonType(ctx, true);
            CheckConversions: if (typeRequest != null)
                {
                // test if we can convert all of the expressions to the decided-upon type
                MethodConstant[] aConvMethod = new MethodConstant[cExprs];
                for (int i = 0; i < cExprs; ++i)
                    {
                    TypeConstant typePre = listExprs.get(i).getType();
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
                        listExprs.set(i, new ConvertExpression(listExprs.get(i), 0, method, errs));
                        }
                    }
                }

            if (typeRequest == null)
                {
                if (fOrdered)
                    {
                    // TODO need a better error
                    log(errs, Severity.ERROR, Compiler.TYPES_NOT_COMPARABLE,
                                listExprs.get(0).getType().getValueString(), "...");
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
            Constant constPrev  = null;
            boolean  fEquality  = operators[0].getId() == Id.COMP_EQ;
            Constant FALSE      = pool.valFalse();
            boolean  fAllConst  = true;

            for (int i = 0; i < cExprs; ++i)
                {
                Expression expr = listExprs.get(i);
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
                                break;
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
                fValid = checkNullComparison(ctx, listExprs, errs);
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

        ConstantPool     pool       = pool();
        List<Expression> listExprs  = expressions;
        int              cExprs     = listExprs.size();
        Argument[]       aArgs      = new Argument[cExprs];
        Constant         constVal   = m_constEq;
        TypeConstant     typeCmp    = m_typeCommon;
        Argument         argResult  = LVal.getLocalArgument();
        Label            labelFalse = new Label("not_eq");
        Label            labelEnd   = new Label("end_eq");

        // evaluate all the sub-expressions upfront - we may need them to produce an
        // assertion exception; local properties should not be used (could cause a "double dip",
        // and only the last argument is allowed to be on stack
        for (int i = 0; i < cExprs; i++)
            {
            Argument arg = listExprs.get(i).generateArgument(ctx, code, false, i == cExprs-1, errs);
            aArgs[i] = i == cExprs-1
                    ? arg
                    : ensurePointInTime(code, arg);
            }

        if (constVal == null)
            {
            // everything has to get compared, left to right
            Token[]    aTokOp = operators;
            int        cOps   = aTokOp.length;
            for (int iCmp = 0; iCmp < cOps; iCmp++)
                {
                Token    tok  = aTokOp[iCmp];
                Argument arg1 = aArgs[iCmp];
                Argument arg2 = aArgs[iCmp + 1];

                if (iCmp == cOps - 1) // last
                    {
                    OpTest opTest = switch (tok.getId())
                        {
                        case COMP_EQ   -> new IsEq(arg1, arg2, argResult);
                        case COMP_NEQ  -> new IsNotEq(arg1, arg2, argResult);
                        case COMP_LT   -> new IsLt(arg1, arg2, argResult);
                        case COMP_GT   -> new IsGt(arg1, arg2, argResult);
                        case COMP_LTEQ -> new IsLte(arg1, arg2, argResult);
                        case COMP_GTEQ -> new IsGte(arg1, arg2, argResult);
                        default        -> throw new IllegalStateException();
                        };

                    opTest.setCommonType(typeCmp);
                    code.add(opTest);
                    }
                else
                    {
                    OpCondJump opCondJump = switch (tok.getId())
                        {
                        case COMP_EQ   -> new JumpNotEq(arg1, arg2, labelFalse);
                        case COMP_NEQ  -> new JumpEq(arg1, arg2, labelFalse);
                        case COMP_LT   -> new JumpGte(arg1, arg2, labelFalse);
                        case COMP_GT   -> new JumpLte(arg1, arg2, labelFalse);
                        case COMP_LTEQ -> new JumpGt(arg1, arg2, labelFalse);
                        case COMP_GTEQ -> new JumpLt(arg1, arg2, labelFalse);
                        default        -> throw new IllegalStateException();
                        };

                    opCondJump.setCommonType(typeCmp);
                    code.add(opCondJump);
                    }
                }
            }
        else
            {
            // optimize equality comparisons to a constant, especially to null
            boolean fNull = constVal.equals(pool.valNull());
            for (int iExpr = 0; iExpr < cExprs; ++iExpr)
                {
                Argument arg = aArgs[iExpr];
                if (iExpr == cExprs - 1) // last
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
        List<Expression> listExprs = expressions;
        int              cExprs    = listExprs.size();
        Argument[]       aArgs     = new Argument[cExprs];
        Constant         constVal  = m_constEq;
        TypeConstant     typeCmp   = m_typeCommon;

        for (int i = 0; i < cExprs; i++)
            {
            Argument arg = listExprs.get(i).generateArgument(ctx, code, false, i == cExprs-1, errs);
            aArgs[i] = i == cExprs-1
                    ? arg
                    : ensurePointInTime(code, arg);
            }

        if (constVal != null)
            {
            boolean fNull = constVal.equals(pool().valNull());
            for (int i = 0; i < cExprs; i++)
                {
                Argument   arg = aArgs[i];
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
        for (int iCmp = 0, cOps = aTokOp.length; iCmp < cOps; ++iCmp)
            {
            Argument arg1 = aArgs[iCmp];
            Argument arg2 = aArgs[iCmp + 1];

            // generate the op that combines the two sub-expressions
            OpCondJump op = switch (aTokOp[iCmp].getId())
                {
                case COMP_EQ ->
                    fWhenTrue
                        ? new JumpEq(arg1, arg2, label)
                        : new JumpNotEq(arg1, arg2, label);

                case COMP_NEQ ->
                    fWhenTrue
                        ? new JumpNotEq(arg1, arg2, label)
                        : new JumpEq(arg1, arg2, label);

                case COMP_LT ->
                    fWhenTrue
                        ? new JumpLt(arg1, arg2, label)
                        : new JumpGte(arg1, arg2, label);

                case COMP_GT ->
                    fWhenTrue
                        ? new JumpGt(arg1, arg2, label)
                        : new JumpLte(arg1, arg2, label);

                case COMP_LTEQ ->
                    fWhenTrue
                        ? new JumpLte(arg1, arg2, label)
                        : new JumpGt(arg1, arg2, label);

                case COMP_GTEQ ->
                    fWhenTrue
                        ? new JumpGte(arg1, arg2, label)
                        : new JumpLt(arg1, arg2, label);

                default ->
                    throw new IllegalStateException();
                };

            op.setCommonType(typeCmp);
            code.add(op);
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
        return expressions.get(0).isCompletable() && expressions.get(1).isCompletable();
        }


    // ----- internal ------------------------------------------------------------------------------

    /**
     * Choose a common type for all the specified expressions.
     *
     * @param ctx       the context
     * @param fCheck
     *
     * @return the selected common type, or null
     */
    protected TypeConstant chooseCommonType(Context ctx, boolean fCheck)
        {
        ConstantPool      pool      = pool();
        boolean           fEqual    = usesEquals();
        List<Expression>  listExprs = expressions;
        Set<TypeConstant> setTried  = new HashSet<>();

        for (int i = 0, c = listExprs.size(); i < c; i++)
            {
            Expression   expr1 = listExprs.get(i);
            TypeConstant type1 = expr1.getImplicitType(ctx);
            if (type1 != null)
                {
                ctx = ctx.enterInferring(type1);
                for (int j = 0; j < c; j++)
                    {
                    if (j == i)
                        {
                        continue;
                        }

                    Expression   expr2 = listExprs.get(j);
                    TypeConstant type2 = expr2.getImplicitType(ctx);
                    TypeConstant typeC = CmpExpression.chooseCommonType(pool, fEqual, type1, type2);

                    if (typeC != null && !setTried.contains(typeC))
                        {
                        ctx = ctx.exit(); // inferring type1
                        if (testCommonType(ctx, typeC))
                            {
                            // no need to exit/discard an inferring context
                            return typeC;
                            }
                        setTried.add(typeC);
                        ctx = ctx.enterInferring(type1);
                        }
                    }
                }
            }

        return null;
        }

    /**
     * Test the specified type for "fit" against all the expression.
     *
     * @param ctx   the compilation context
     * @param type  the type to test
     *
     * @return true iff all the expressions "fit"
     */
    protected boolean testCommonType(Context ctx, TypeConstant type)
        {
        ctx = ctx.enterInferring(type);

        for (Expression expr : expressions)
            {
            if (!expr.testFit(ctx, type, false, null).isFit())
                {
                return false;
                }
            }

        // there is no need to exit/discard an inferring context
        return true;
        }

    boolean checkNullComparison(Context ctx, List<Expression> listExprs, ErrorListener errs)
        {
        TypeConstant typeNull = pool().typeNull();
        for (Expression expr : listExprs)
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
        StringBuilder sb = new StringBuilder();
        sb.append(expressions.get(0));
        for (int i = 0, c = operators.length; i < c; ++i)
            {
            sb.append(' ')
              .append(operators[i].getId().TEXT)
              .append(' ')
              .append(expressions.get(i+1));
            }
        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected List<Expression> expressions;
    protected Token[]          operators;

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