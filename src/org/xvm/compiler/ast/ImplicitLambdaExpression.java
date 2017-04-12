package org.xvm.compiler.ast;


import org.xvm.compiler.Token;

import org.xvm.util.ListMap;

import java.util.List;
import java.util.Map;


/**
 * Lambda expression is an inlined function. This version uses parameters that are assumed to be
 * names only.
 *
 * @author cp 2017.04.07
 */
public class ImplicitLambdaExpression
        extends Expression
    {
    // ----- constructors --------------------------------------------------------------------------

    public ImplicitLambdaExpression(List<Expression> params, Token operator, StatementBlock body)
        {
        this.params   = params;
        this.operator = operator;
        this.body     = body;
        }


    // ----- accessors -----------------------------------------------------------------------------


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append('(');
        boolean first = true;
        for (Expression param : params)
            {
            if (first)
                {
                first = false;
                }
            else
                {
                sb.append(", ");
                }
            sb.append(param);
            }

        sb.append(')')
          .append(' ')
          .append(operator.getId().TEXT)
          .append(' ')
          .append(body);

        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }

    @Override
    public Map<String, Object> getDumpChildren()
        {
        ListMap<String, Object> map = new ListMap();
        map.put("params", params);
        map.put("body", body);
        return map;
        }


    // ----- fields --------------------------------------------------------------------------------

    protected List<Expression> params;
    protected Token            operator;
    protected StatementBlock   body;
    }
