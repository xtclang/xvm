package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.Collections;
import java.util.List;

import org.xvm.asm.ConstantPool;
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
import org.xvm.compiler.ErrorListener;
import org.xvm.compiler.Token;

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
    protected boolean validate(Context ctx, ErrorListener errs)
        {
        boolean fValid = true;

        // validate the return value expressions
        List<Expression> listExprs = this.exprs;
        int              cExprs    = listExprs == null ? 0 : listExprs.size();
        for (int i = 0; i < cExprs; ++i)
            {
            fValid &= listExprs.get(i).validate(ctx, errs);
            }

        // check for special return modes: tuple-returns and conditional-returns
        MethodStructure structMethod = ctx.getMethod();
        int             cReturns     = structMethod.getReturnCount();
        List<Parameter> listRets     = structMethod.getReturns();
        if (cExprs == 1 && (cReturns > 1 || cReturns == 1 && !listRets.get(0).getType().isTuple())
                && listExprs.get(0).getImplicitType().isTuple())
            {
            // if there is exactly 1 expression, it results in a tuple type, and the return type of
            // the method is NOT a single tuple, then the return will generate a RETURN_T op
            m_fTupleReturn = true;
            }
        else if (cExprs == 1 && structMethod.isConditionalReturn() && exprs.get(0).isConstantFalse())
            {
            // if there is exactly 1 expression, and it is "false", then that is a valid return
            // from a method with a conditional-return
            m_fCondReturn = true;
            }
        else if (cExprs != cReturns)
            {
            // error: unexpected number of return value expressions
            if (cExprs == 0)
                {
                log(errs, Severity.ERROR, Compiler.RETURN_EXPECTED);
                }
            else if (cReturns == 0)
                {
                log(errs, Severity.ERROR, Compiler.RETURN_VOID);
                }
            else
                {
                log(errs, Severity.ERROR, Compiler.RETURN_WRONG_COUNT, cReturns, cExprs);
                }
            fValid = false;
            }

        return fValid;
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

    protected Token            keyword;
    protected List<Expression> exprs;
    protected boolean          m_fTupleReturn;
    protected boolean          m_fCondReturn;

    private static final Field[] CHILD_FIELDS = fieldsForNames(ReturnStatement.class, "exprs");
    }
