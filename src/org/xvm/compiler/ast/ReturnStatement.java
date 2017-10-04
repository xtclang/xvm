package org.xvm.compiler.ast;



import org.xvm.asm.MethodStructure;
import org.xvm.asm.Op.Argument;
import org.xvm.asm.Parameter;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.op.Return_0;

import org.xvm.asm.op.Return_1;
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
                if (cExprs == 0) // TODO consider a Tuple<> also being ok?
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

            default:
                if (cExprs == cReturns)
                    {
                    // TODO
                    }

            }

        // TODO have to make sure that types are resolved before we get to this stage, e.g. Void means 0 return values
        // TODO what is the expected number of return values?
        // TODO how to tell the expression the type(s) of the expected results?
        // TODO how to report errors?
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
