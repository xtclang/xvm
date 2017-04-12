package org.xvm.compiler.ast;


import org.xvm.compiler.Token;

import org.xvm.util.ListMap;

import java.util.List;
import java.util.Map;


/**
 * If you already have an expression "expr", this is for "expr.name".
 *
 * @author cp 2017.04.08
 */
public class DotNameExpression
        extends Expression
    {
    // ----- constructors --------------------------------------------------------------------------

    public DotNameExpression(Expression expr, Token name, List<TypeExpression> params)
        {
        this.expr   = expr;
        this.name   = name;
        this.params = params;
        }


    // ----- accessors -----------------------------------------------------------------------------


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append(expr)
          .append('.')
          .append(name.getValue());

        if (params != null)
            {
            sb.append('<');
            boolean first = true;
            for (TypeExpression type : params)
                {
                if (first)
                    {
                    first = false;
                    }
                else
                    {
                    sb.append(", ");
                    }
                sb.append(type);
                }
            sb.append('>');
            }

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
        map.put("expr", expr);
        map.put("name", name);
        map.put("params", params);
        return map;
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Expression           expr;
    protected Token                name;
    protected List<TypeExpression> params;
    }
