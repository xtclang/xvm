package org.xvm.compiler.ast;



import java.util.ArrayList;
import java.util.stream.Collectors;
import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op.Argument;
import org.xvm.asm.Parameter;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.op.Return_0;

import org.xvm.asm.op.Return_1;
import org.xvm.asm.op.Return_N;
import org.xvm.asm.op.Return_T;
import org.xvm.compiler.Compiler;
import org.xvm.compiler.ErrorListener;
import org.xvm.compiler.Token;

import java.lang.reflect.Field;

import java.util.Collections;
import java.util.List;
import org.xvm.util.Severity;


/**
 * A return statement specifies a return with optional values.
 *
 * @author cp 2017.04.03
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
    public void emit(Code code, ErrorListener errs)
        {
        // TODO simplify() pass must already have been done before this!

        // first determine what the method declaration indicates the return value is (none, one,
        // or multi)
        MethodStructure  structMethod = code.getMethodStructure();
        int              cReturns     = structMethod.getReturnCount();
        List<Parameter>  listRets     = structMethod.getReturns();
        List<Expression> listExprs    = this.exprs;
        int              cExprs       = listExprs == null ? 0 : listExprs.size();
        switch (cReturns)
            {
            case 0:
                if (cExprs == 0) // TODO consider a value of type Tuple<> also being ok? i.e. "|| isEmptyTuple(v))"
                    {
                    code.add(new Return_0());
                    }
                else
                    {
                    log(errs, Severity.ERROR, Compiler.RETURN_VOID);
                    }
                break;

            case 1:
                if (cExprs == 1)
                    {
                    Argument arg = listExprs.get(0).generateArgument(
                            code, listRets.get(0).getType(), false, errs);
                    if (arg != null)
                        {
                        code.add(new Return_1(arg));
                        }
                    }
                else
                    {
                    // most of the time this is an error
                    // TODO what if return type is tuple - do multiple expressions go into it?
                    log(errs, Severity.ERROR, cExprs == 0
                            ? Compiler.RETURN_EXPECTED
                            : Compiler.RETURN_WRONG_COUNT);
                    }
                break;

            default:
                if (cExprs == cReturns)
                    {
                    Argument[] args = new Argument[cExprs];
                    for (int i = 0; i < cExprs; ++i)
                        {
                        args[i] = listExprs.get(0).generateArgument(
                                code, listRets.get(0).getType(), false, errs);
                        }
                    code.add(new Return_N(args));
                    }
                else if (cExprs == 1)
                    {
                    // assume it's a tuple
                    List<Argument> args = listExprs.get(0).generateArguments(code, listRets.stream()
                            .map(p -> p.getType()).collect(Collectors.toList()), true, errs);
                    int cArgs = args.size();
                    if (cArgs == cReturns)
                        {
                        code.add(new Return_N(args.toArray(new Argument[cReturns])));
                        }
                    else if (cArgs == 1)
                        {
                        code.add(new Return_T(args.get(0)));
                        }
                    }
                break;
            }
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

    private static final Field[] CHILD_FIELDS = fieldsForNames(ReturnStatement.class, "exprs");
    }
