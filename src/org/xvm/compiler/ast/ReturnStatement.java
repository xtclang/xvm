package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.Collections;
import java.util.List;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Op.Argument;
import org.xvm.asm.Parameter;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.Return_0;
import org.xvm.asm.op.Return_1;
import org.xvm.asm.op.Return_N;
import org.xvm.asm.op.Return_T;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Token;

import org.xvm.compiler.ast.Expression.TuplePref;
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
        this(keyword, Collections.singletonList(expr));
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
    protected Statement validate(Context ctx, ErrorListener errs)
        {
        boolean fValid = true;

        MethodStructure  structMethod = ctx.getMethod();
        boolean          fConditional = structMethod.isConditionalReturn();
        TypeConstant[]   aRetTypes    = structMethod.getReturnTypes();
        int              cRets        = aRetTypes.length;
        List<Expression> listExprs    = this.exprs;
        int              cExprs       = listExprs == null ? 0 : listExprs.size();

        // Void methods are the simplest
        if (cExprs == 0 || cRets == 0)
            {
            if (cExprs > 0)
                {
                // check the expressions anyhow (even though they can't be used)
                for (int i = 0; i < cExprs; ++i)
                    {
                    listExprs.get(i).validate(ctx, null, TuplePref.Accepted, errs);
                    }

                // allow the (strange) use of T0D0 or the (strange) return of a Void expression
                if (cExprs != 1 || !listExprs.get(0).isAborting() || !listExprs.get(0).isVoid())
                    {
                    // it was supposed to be a void return
                    log(errs, Severity.ERROR, Compiler.RETURN_VOID);
                    fValid = false;
                    }
                }
            else if (cRets > 0)
                {
                // the expressions are missing; it was NOT supposed to be a void return
                log(errs, Severity.ERROR, Compiler.RETURN_EXPECTED);
                fValid = false;
                }
            }
        else if (cExprs > 1)
            {
            // validate each expression, telling it what return type is expected
            for (int i = 0; i < cExprs; ++i)
                {
                TypeConstant typeRet = i < cRets
                        ? aRetTypes[i]
                        : null;
                Expression exprOld = listExprs.get(i);
                Expression exprNew = exprOld.validate(ctx, typeRet, TuplePref.Rejected, errs);
                if (exprNew != exprOld)
                    {
                    fValid &= exprNew != null;
                    if (exprNew != null)
                        {
                        listExprs.set(i, exprNew);
                        }
                    }
                }

            // make sure the arity is correct (the number of exprs has to match the number of rets)
            if (cExprs != cRets)
                {
                log(errs, Severity.ERROR, Compiler.RETURN_WRONG_COUNT, cRets, cExprs);
                }
            }
        else // cExprs == 1
            {
            Expression expr       = listExprs.get(0);

            // TODO TODO TODO

            int        cValues    = expr.getValueCount();
            boolean    fCondFalse = false;
            if (cValues > 1 && cRets > 1)
                {
                // there is exactly 1 expression, and it results in multiple values, so allow a
                // tuple return that will generate a RETURN_T op
                fValid &= expr.validateMulti(ctx, structMethod.getReturnTypes(), errs);
                m_fTupleReturn = true;
                }
            else if (fConditional)
                {
                // it's allowed to have a single conditional return value, as long as it's False
                assert aRetTypes[0].getType().equals(pool().typeBoolean());

                fValid &= expr.validate(ctx, pool().typeBoolean(), errs);

                fCondFalse = fValid && expr.isConstant() && expr.toConstant().equals(pool().valFalse());
                }
            else
                {
                // assume a simple 1:1 expression to return value mapping
                fValid &= expr.validate(ctx, aRetTypes[0].getType(), errs);
                }

            // verify that we had enough arguments from the expression to satisfy the # of returns
            // (we could treat extras as an error, but consider the example of the expression being
            // a method invocation returning 4 items, and we just want 3 of them, so we'll
            // black-hole them)
            if (!expr.isAborting() && cValues < cRets && !fCondFalse)
                {
                log(errs, Severity.ERROR, Compiler.RETURN_WRONG_COUNT, cRets, cExprs);
                }
            }

        return fValid
                ? this
                : null;
        }

    @Override
    protected boolean emit(Context ctx, boolean fReachable, Code code, ErrorListener errs)
        {
        // first determine what the method declaration indicates the return value is (none, one,
        // or multi)
        MethodStructure  structMethod = ctx.getMethod();
        int              cReturns     = structMethod.getReturnCount();
        List<Parameter>  listRets     = structMethod.getReturns();
        List<Expression> listExprs    = this.exprs;
        int              cExprs       = listExprs == null ? 0 : listExprs.size();

        if (m_fTupleReturn)
            {
            // the return statement has a single expression; the type that the expression has to
            // generate is the "tuple of" all of the return types
            TypeConstant[] atypeR = new TypeConstant[cReturns];
            for (int i = 0; i < cReturns; ++i)
                {
                atypeR[i] = listRets.get(i).getType();
                }

            ConstantPool pool   = pool();
            TypeConstant typeT  = pool.ensureParameterizedTypeConstant(pool.typeTuple(), atypeR);
            Argument     arg    = listExprs.get(0).generateArgument(code, typeT, false, errs);
            code.add(new Return_T(arg));
            }
        else
            {
            switch (cExprs)
                {
                case 0:
                    code.add(new Return_0());
                    break;

                case 1:
                    Argument arg = listExprs.get(0).generateArgument(
                            code, listRets.get(0).getType(), false, errs);
                    code.add(new Return_1(arg));
                    break;

                default:
                    Argument[] args = new Argument[cExprs];
                    for (int i = 0; i < cExprs; ++i)
                        {
                        args[i] = listExprs.get(i).generateArgument(
                                code, listRets.get(i).getType(), false, errs);
                        }
                    code.add(new Return_N(args));
                    break;
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

    protected transient boolean m_fTupleReturn;

    private static final Field[] CHILD_FIELDS = fieldsForNames(ReturnStatement.class, "exprs");
    }
