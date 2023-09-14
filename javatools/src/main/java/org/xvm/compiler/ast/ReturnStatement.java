package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.Arrays;
import java.util.List;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Register;

import org.xvm.asm.ast.BinaryAST;
import org.xvm.asm.ast.ExprAST;
import org.xvm.asm.ast.ReturnStmtAST;
import org.xvm.asm.ast.UnpackExprAST;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.Jump;
import org.xvm.asm.op.JumpFalse;
import org.xvm.asm.op.Label;
import org.xvm.asm.op.Move;
import org.xvm.asm.op.Return_0;
import org.xvm.asm.op.Return_1;
import org.xvm.asm.op.Return_N;
import org.xvm.asm.op.Return_T;
import org.xvm.asm.op.Var_D;

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
        this(keyword, Arrays.asList(expr)); // mutable list
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
            // for now, we don't allow any conditional parts of a multi-return statement
            return false;
            }

        AstNode container = getCodeContainer();
        if (container.isReturnConditional())
            {
            return true;
            }

        TypeConstant[] atypeRet = container.getReturnTypes();
        int            cRets    = atypeRet == null ? 0 : atypeRet.length;

        return switch (cRets)
            {
            case 0  -> true;
            case 1  -> atypeRet[0].equals(pool().typeBoolean());
            default -> false;
            };
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

        // resolve auto-narrowing; don't mutate aRetTypes!
        boolean fClone = true;
        for (int i = 0; i < cRets; i++)
            {
            TypeConstant typeRet = aRetTypes[i];
            if (typeRet.containsAutoNarrowing(false))
                {
                if (fClone)
                    {
                    aRetTypes = aRetTypes.clone();
                    fClone    = false;
                    }
                aRetTypes[i] = typeRet.resolveAutoNarrowing(pool, false, ctx.getThisType(), null);
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

                if (fValid)
                    {
                    if (cExprs == 1)
                        {
                        // allow the (strange) use of T0D0, the return of a void expression
                        // or an invocation that is not void
                        Expression expr = listExprs.get(0);
                        if (expr.isCompletable() && !expr.isVoid() &&
                                !(expr instanceof InvocationExpression))
                            {
                            // it is supposed to be a void return; allow a Future<Tuple>
                            if (expr instanceof NameExpression exprName &&
                                    exprName.isDynamicVar() && expr.getType().isTuple())
                                {
                                m_fFutureReturn = true;
                                }
                            else if (expr instanceof TupleExpression exprTuple &&
                                    exprTuple.getExpressions().size() == 0 &&
                                    exprTuple.getType().isImmutable())
                                {
                                // allow Tuple:()
                                m_fTupleReturn = true;
                                }
                            else
                                {
                                log(errs, Severity.ERROR, Compiler.RETURN_VOID);
                                fValid = false;
                                }
                            }
                        }
                    else
                        {
                        log(errs, Severity.ERROR, Compiler.RETURN_WRONG_COUNT, cRets, cExprs);
                        fValid = false;
                        }
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
            if (fConditional && exprOld instanceof TernaryExpression exprTernary)
                {
                // ternary expression needs to know the fact that it returns a conditional type
                m_fConditionalTernary = true;
                exprTernary.markConditional();
                typeRequired = cRets == 2 ? aRetTypes[1] : null;
                }

            if (typeRequired != null)
                {
                ctx = ctx.enterInferring(typeRequired);
                }

            // let's test several possibilities:
            do
                {
                // - most likely the expression matches the return types for the method
                if (cRets < 0 || exprOld.testFitMulti(ctx, aRetTypes, false, null).isFit())
                    {
                    exprNew = exprOld.validateMulti(ctx, aRetTypes, errs);
                    break;
                    }

                // - it could be a conditional false
                if (fConditional && exprOld.testFit(ctx, pool.typeFalse(), false, null).isFit())
                    {
                    exprNew = exprOld.validate(ctx, pool.typeFalse(), errs);
                    if (exprNew != null && (!exprNew.isConstant() || !exprNew.toConstant().equals(pool.valFalse())))
                        {
                        // it's not clear how this could happen; it's more like an assertion
                        log(errs, Severity.ERROR, Compiler.CONSTANT_REQUIRED);
                        fValid = false;
                        }
                    break;
                    }

                // - it could be a Future return
                if (cRets == 1)
                    {
                    TypeConstant typeFuture = pool.ensureFutureVar(aRetTypes[0]);
                    if (exprOld.testFit(ctx, typeFuture, false, null).isFit())
                        {
                        exprNew = exprOld.validate(ctx, typeFuture, errs);
                        m_fFutureReturn = true;
                        break;
                        }
                    }

                // - it could be a tuple return
                TypeConstant typeTuple = pool.ensureTupleType(aRetTypes);
                if (exprOld.testFit(ctx, typeTuple, false, null).isFit())
                    {
                    exprNew = exprOld.validate(ctx, typeTuple, errs);
                    m_fTupleReturn = true;
                    break;
                    }

                // - otherwise it's most probably an error and the validation will log it
                //   (except cases when testFit() implementation doesn't fully match the "validate"
                //    logic or somehow has more information to operate on, such as type inference)
                exprNew = exprOld.validateMulti(ctx, aRetTypes, errs);
                }
            while (false);

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

        if (container instanceof StatementExpression exprStmt)
            {
            assert !fConditional;

            // emit() for a return inside a StatementExpression produces an assignment from the
            // expression
            // TODO m_fTupleReturn, m_fConditionalTernary, m_fFutureReturn
            Assignable[]        aLVal  = exprStmt.getAssignables();
            int                 cLVals = aLVal.length;
            ExprAST<Constant>[] aAst   = new ExprAST[cLVals];
            for (int i = 0, cExprs = exprs == null ? 0 : exprs.size(); i < cExprs; ++i)
                {
                Expression expr = exprs.get(i);
                if (i < cLVals)
                    {
                    expr.generateAssignment(ctx, code, aLVal[i], errs);
                    }
                else
                    {
                    expr.generateVoid(ctx, code, errs);
                    }
                aAst[i] = expr.getExprAST();
                }
            code.add(new Jump(exprStmt.body.getEndLabel()));

            ctx.getHolder().setAst(this, new ReturnStmtAST<>(aAst));

            // "return" does not complete
            return false;
            }

        // first determine what the method declaration indicates the return value is (none, one,
        // or multi)
        ConstantPool        pool      = pool();
        TypeConstant[]      atypeRets = container.getReturnTypes();
        int                 cRets     = atypeRets == null ? 0 : atypeRets.length;
        List<Expression>    listExprs = this.exprs;
        int                 cExprs    = listExprs == null ? 0 : listExprs.size();
        BinaryAST<Constant> astResult;

        if (m_fTupleReturn)
            {
            assert cExprs == 1;

            // the return statement has a single expression returning a tuple; the caller expects
            // multiple returns
            Expression expr = listExprs.get(0);
            Argument   arg  = expr.generateArgument(ctx, code, true, true, errs);
            code.add(new Return_T(arg));

            astResult = new UnpackExprAST<>(expr.getExprAST(),
                            atypeRets == null ? TypeConstant.NO_TYPES : atypeRets);
            }
        else if (m_fConditionalTernary)
            {
            TernaryExpression expr = (TernaryExpression) listExprs.get(0);
            expr.generateConditionalReturn(ctx, code, errs);

            astResult = expr.getExprAST();
            }
        else
            {
            // Note: it's a responsibility of the conditional return to *not* return anything else
            //       if the value at index 0 is "False"
            switch (cExprs)
                {
                case 0:
                    code.add(new Return_0());
                    astResult = new ReturnStmtAST<>(null);
                    break;

                case 1:
                    {
                    // we need to get all the arguments the expression can provide, but
                    // return only as many as the caller expects
                    Expression expr   = listExprs.get(0);
                    boolean    fCheck = fConditional && !expr.isConditionalResult();
                    Argument[] aArgs  = expr.generateArguments(ctx, code, true, !fCheck, errs);
                    int        cArgs  = aArgs.length;

                    switch (cRets)
                        {
                        case 0:
                            if (m_fFutureReturn)
                                {
                                code.add(new Return_1(aArgs[0]));
                                }
                            else
                                {
                                code.add(new Return_0());
                                }
                            break;

                        case 1:
                            {
                            Argument argRet = aArgs[0];
                            if (m_fFutureReturn)
                                {
                                // create an intermediate dynamic var
                                Register regFuture = code.createRegister(
                                        pool.ensureFutureVar(argRet.getType().getParamType(0)));
                                code.add(new Var_D(regFuture));
                                code.add(new Move(argRet, regFuture));
                                code.add(new Return_1(regFuture));
                                }
                            else
                                {
                                code.add(new Return_1(argRet));
                                }
                            break;
                            }

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
                                    code.add(new Return_1(pool.valFalse()));
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
                    astResult = new ReturnStmtAST<>(new ExprAST[]{expr.getExprAST()});
                    break;
                    }

                default:
                    {
                    assert cRets == cExprs;

                    Argument[]          aArgs = new Argument[cRets];
                    ExprAST<Constant>[] aASTs = new ExprAST[cRets];
                    for (int i = 0; i < cRets; ++i)
                        {
                        Expression expr = listExprs.get(i);
                        Argument   arg  = expr.generateArgument(ctx, code, true, !fConditional || i > 0, errs);

                        aArgs[i] = i == cExprs-1
                                ? arg
                                : expr.ensurePointInTime(code, arg);
                        aASTs[i] = expr.getExprAST();
                        }

                    Label labelFalse = fConditional ? new Label("false") : null;
                    if (fConditional)
                        {
                        code.add(new JumpFalse(aArgs[0], labelFalse));
                        }

                    code.add(new Return_N(aArgs));

                    if (fConditional)
                        {
                        code.add(labelFalse);
                        code.add(new Return_1(pool.valFalse()));
                        }
                    astResult = new ReturnStmtAST<>(aASTs);
                    break;
                    }
                }
            }

        ctx.getHolder().setAst(this, astResult);

        // return never completes
        return false;
        }

    @Override
    public boolean isCompletable()
        {
        // example to consider: "return result?;"
        return exprs != null && exprs.stream().anyMatch(Expression::isShortCircuiting);
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
    protected transient boolean m_fFutureReturn;

    private static final Field[] CHILD_FIELDS = fieldsForNames(ReturnStatement.class, "exprs");
    }