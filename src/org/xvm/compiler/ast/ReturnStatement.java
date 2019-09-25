package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.Arrays;
import java.util.List;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.JumpFalse;
import org.xvm.asm.op.Label;
import org.xvm.asm.op.Return_0;
import org.xvm.asm.op.Return_1;
import org.xvm.asm.op.Return_N;
import org.xvm.asm.op.Return_T;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Token;

import org.xvm.compiler.ast.Expression.Assignable;

import org.xvm.util.Severity;


/**
 * A return statement specifies a return with optional values.
 */
public class ReturnStatement
        extends Statement
    {
    // ----- constructors --------------------------------------------------------------------------

    public ReturnStatement(Token keyword)
        {
        this(keyword, (List) null);
        }

    public ReturnStatement(Token keyword, Expression expr)
        {
        this(keyword, Arrays.asList(expr));
        }

    public ReturnStatement(Token keyword, List<Expression> exprs)
        {
        this.keyword = keyword;
        this.exprs   = exprs;
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return the expression(s) that the return statement returns, or null if there are no
     *         expressions
     */
    public List<Expression> getExpressions()
        {
        return exprs;
        }

    @Override
    public long getStartPosition()
        {
        return keyword.getStartPosition();
        }

    @Override
    public long getEndPosition()
        {
        return exprs == null ? keyword.getEndPosition() : exprs.get(exprs.size()-1).getEndPosition();
        }

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }


    // ----- compilation ---------------------------------------------------------------------------


    @Override
    protected boolean allowsConditional(Expression exprChild)
        {
        if (exprs.size() > 1)
            {
            // for now we don't allow any conditional parts of a multi-return statement
            return false;
            }

        AstNode container = getCodeContainer();
        if (container.isReturnConditional())
            {
            return true;
            }

        TypeConstant[] atypeRet = container.getReturnTypes();
        switch (atypeRet.length)
            {
            case 0:
                return true;

            case 1:
                return atypeRet[0].equals(pool().typeBoolean());

            default:
                return false;
            }
        }

    @Override
    protected boolean allowsShortCircuit(AstNode nodeChild)
        {
        return true;
        }

    @Override
    protected Statement validateImpl(Context ctx, ErrorListener errs)
        {
        ConstantPool     pool         = pool();
        boolean          fValid       = true;
        AstNode          container    = getCodeContainer();
        TypeConstant[]   aRetTypes    = container.getReturnTypes();
        boolean          fConditional = container.isReturnConditional();
        int              cRets        = aRetTypes == null ? -1 : aRetTypes.length;
        List<Expression> listExprs    = this.exprs;
        int              cExprs       = listExprs == null ? 0 : listExprs.size();
        TypeConstant[]   atypeActual  = null;

        // resolve auto-narrowing
        for (int i = 0; i < cRets; i++)
            {
            TypeConstant typeRet = aRetTypes[i];
            if (typeRet.isAutoNarrowing())
                {
                aRetTypes[i] = typeRet.resolveAutoNarrowing(pool, false, ctx.getThisType());
                }
            }

        // void methods are the simplest
        if (cExprs == 0 || cRets == 0)
            {
            atypeActual = TypeConstant.NO_TYPES;

            if (cExprs > 0)
                {
                // check the expressions anyhow (even though they can't be used)
                atypeActual = validateExpressions(ctx, listExprs, null, errs);
                fValid      = atypeActual != null;

                if (cExprs == 1)
                    {
                    // allow the (strange) use of T0D0, the (strange) return of a void expression
                    // or an invocation that is not void
                    Expression expr = listExprs.get(0);
                    if (expr.isCompletable() && !expr.isVoid() &&
                            !(expr instanceof InvocationExpression))
                        {
                        // it was supposed to be a void return
                        log(errs, Severity.ERROR, Compiler.RETURN_VOID);
                        fValid = false;
                        }
                    }
                else
                    {
                    log(errs, Severity.ERROR, Compiler.RETURN_WRONG_COUNT, cRets, cExprs);
                    fValid = false;
                    }
                }
            else if (cRets > 0) // cExprs == 0
                {
                // the expressions are missing; it was NOT supposed to be a void return
                log(errs, Severity.ERROR, Compiler.RETURN_EXPECTED);
                fValid = false;
                }
            }
        else if (cExprs > 1)
            {
            // validate each expression, telling it what return type is expected
            atypeActual = validateExpressions(ctx, listExprs, aRetTypes, errs);
            fValid      = atypeActual != null;

            // make sure the arity is correct (the number of exprs has to match the number of returns)
            if (cRets >= 0 && cExprs != cRets)
                {
                log(errs, Severity.ERROR, Compiler.RETURN_WRONG_COUNT, cRets, cExprs);
                }
            }
        else // cExprs == 1
            {
            Expression exprOld = listExprs.get(0);
            Expression exprNew;

            TypeConstant typeRequired = cRets >= 1 ? aRetTypes[0] : null;
            if (fConditional && exprOld instanceof TernaryExpression)
                {
                // ternary expression needs to know the fact that it returns a conditional type
                m_fConditionalTernary = true;
                ((TernaryExpression) exprOld).markConditional();
                typeRequired = cRets == 2 ? aRetTypes[1] : null;
                }

            if (typeRequired != null)
                {
                ctx = ctx.enterInferring(typeRequired);
                }

            // several possibilities:
            // 1) most likely the expression matches the return types for the method
            if (cRets < 0 || exprOld.testFitMulti(ctx, aRetTypes, null).isFit())
                {
                exprNew = exprOld.validateMulti(ctx, aRetTypes, errs);
                }
            else
                {
                // 2) it could be a conditional false
                if (fConditional && exprOld.testFit(ctx, pool.typeFalse(), null).isFit())
                    {
                    exprNew = exprOld.validate(ctx, pool.typeFalse(), errs);
                    if (exprNew != null && (!exprNew.isConstant() || !exprNew.toConstant().equals(pool.valFalse())))
                        {
                        // it's not clear how this could happen; it's more like an assertion
                        log(errs, Severity.ERROR, Compiler.CONSTANT_REQUIRED);
                        fValid = false;
                        }
                    }
                else
                    {
                    // 3) it could be a tuple return
                    TypeConstant typeTuple = pool.ensureParameterizedTypeConstant(pool.typeTuple(), aRetTypes);
                    if (exprOld.testFit(ctx, typeTuple, null).isFit())
                        {
                        exprNew = exprOld.validate(ctx, typeTuple, errs);
                        m_fTupleReturn = true;
                        }
                    // 4) otherwise it's most probably an error and the validation will log it
                    //   (except cases when testFit() implementation doesn't fully match the validate
                    //    logic or somehow has more information to operate on, such as type inference)
                    else
                        {
                        exprNew = exprOld.validateMulti(ctx, aRetTypes, errs);
                        }
                    }
                }

            if (typeRequired != null)
                {
                ctx = ctx.exit();
                }

            if (exprNew != exprOld)
                {
                fValid &= exprNew != null;
                if (exprNew != null)
                    {
                    listExprs.set(0, exprNew);
                    }
                }

            if (fValid)
                {
                atypeActual = m_fTupleReturn
                        ? exprNew.getType().getParamTypesArray()
                        : exprNew.getTypes();
                }
            }

        ctx.setReachable(false);
        if (fValid)
            {
            container.collectReturnTypes(atypeActual);
            return this;
            }
        else
            {
            container.collectReturnTypes(null);
            return null;
            }
        }

    @Override
    protected boolean emit(Context ctx, boolean fReachable, Code code, ErrorListener errs)
        {
        AstNode container    = getCodeContainer();
        boolean fConditional = container.isReturnConditional();

        if (container instanceof StatementExpression)
            {
            assert !fConditional;

            // emit() for a return inside a StatementExpression produces an assignment from the
            // expression
            // TODO m_fTupleReturn
            Assignable[] aLVals = ((StatementExpression) container).getAssignables();
            int          cLVals = aLVals.length;
            for (int i = 0, cExprs = exprs == null ? 0 : exprs.size(); i < cExprs; ++i)
                {
                if (i < cLVals)
                    {
                    exprs.get(i).generateAssignment(ctx, code, aLVals[i], errs);
                    }
                else
                    {
                    exprs.get(i).generateVoid(ctx, code, errs);
                    }
                }

            // "return" does not complete
            return false;
            }

        // first determine what the method declaration indicates the return value is (none, one,
        // or multi)
        TypeConstant[]   atypeRets = container.getReturnTypes();
        int              cRets     = atypeRets == null ? 0 : atypeRets.length;
        List<Expression> listExprs = this.exprs;
        int              cExprs    = listExprs == null ? 0 : listExprs.size();

        if (m_fTupleReturn)
            {
            // the return statement has a single expression; the type that the expression has to
            // generate is the "tuple of" all of the return types
            // TODO cExprs != 1
            Argument arg = listExprs.get(0).generateArgument(ctx, code, true, true, errs);
            code.add(new Return_T(arg));
            }
        else if (m_fConditionalTernary)
            {
            ((TernaryExpression) listExprs.get(0)).generateConditionalReturn(ctx, code, errs);
            }
        else
            {
            // Note: it's a responsibility of the conditional return to *not* return anything else
            //       if the value at index 0 is "False"
            switch (cExprs)
                {
                case 0:
                    code.add(new Return_0());
                    break;

                case 1:
                    {
                    Expression expr = listExprs.get(0);
                    if (expr.isVoid())
                        {
                        code.add(new Return_0());
                        }

                    // we need to get all the arguments the expression can provide. but
                    // return only as many as the caller expects
                    boolean    fCheck = fConditional && !expr.isConditionalResult();
                    Argument[] aArgs  = expr.generateArguments(ctx, code, true, !fCheck, errs);
                    int        cArgs  = aArgs.length;

                    switch (cRets)
                        {
                        case 0:
                            code.add(new Return_0());
                            break;

                        case 1:
                            code.add(new Return_1(aArgs[0]));
                            break;

                        default:
                            if (cArgs > 1)
                                {
                                Label labelFalse = fCheck ? new Label("false") : null;

                                if (fCheck)
                                    {
                                    code.add(new JumpFalse(aArgs[0], labelFalse));
                                    }

                                if (cArgs == cRets)
                                    {
                                    code.add(new Return_N(aArgs));
                                    }
                                else
                                    {
                                    code.add(new Return_N(Arrays.copyOfRange(aArgs, 0, cRets)));
                                    }

                                if (fCheck)
                                    {
                                    code.add(labelFalse);
                                    code.add(new Return_1(pool().valFalse()));
                                    }
                                }
                            else
                                {
                                assert fConditional;

                                Constant valFalse = pool().valFalse();
                                if (expr.isConstant() && expr.toConstant().equals(valFalse))
                                    {
                                    code.add(new Return_1(valFalse));
                                    }
                                else
                                    {
                                    log(errs, Severity.ERROR, Compiler.WRONG_TYPE,
                                        valFalse.getValueString(), expr.getType().getValueString());
                                    }
                                }
                        }
                    break;
                    }

                default:
                    {
                    assert cRets == cExprs;

                    boolean fCheck = fConditional;

                    Argument[] aArgs = new Argument[cRets];
                    for (int i = 0; i < cRets; ++i)
                        {
                        aArgs[i] = listExprs.get(i).generateArgument(ctx, code, true, i > 0 || !fCheck, errs);
                        }

                    Label labelFalse = fCheck ? new Label("false") : null;
                    if (fCheck)
                        {
                        code.add(new JumpFalse(aArgs[0], labelFalse));
                        }

                    code.add(new Return_N(aArgs));

                    if (fCheck)
                        {
                        code.add(labelFalse);
                        code.add(new Return_1(pool().valFalse()));
                        }
                    break;
                    }
                }
            }

        // return never completes
        return false;
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();
        sb.append("return");
        if(exprs != null)
            {
            switch (exprs.size())
                {
                case 0:
                    break;

                case 1:
                    sb.append(' ')
                      .append(exprs.get(0));
                    break;

                default:
                    boolean first = true;
                    for (Expression expr : exprs)
                        {
                        if (first)
                            {
                            first = false;
                            sb.append(" ");
                            }
                        else
                            {
                            sb.append(", ");
                            }
                        sb.append(expr);
                        }
                    break;
                }
            }
        sb.append(';');
        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Token             keyword;
    protected List<Expression>  exprs;

    protected transient boolean m_fConditionalTernary;
    protected transient boolean m_fTupleReturn;

    private static final Field[] CHILD_FIELDS = fieldsForNames(ReturnStatement.class, "exprs");
    }
