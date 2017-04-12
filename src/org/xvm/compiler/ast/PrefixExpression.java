package org.xvm.compiler.ast;


import org.xvm.compiler.Token;

import org.xvm.util.ListMap;

import java.util.Map;

/**
 * Generic expression for something that follows the pattern "operator expression".
 *
 * @author cp 2017.04.06
 */
public class PrefixExpression
        extends Expression
    {
    // ----- constructors --------------------------------------------------------------------------

    public PrefixExpression(Token operator, Expression expr)
        {
        this.operator = operator;
        this.expr     = expr;
        }


    // ----- accessors -----------------------------------------------------------------------------


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append(operator.getId().TEXT)
          .append(expr);

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
        return map;
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Token      operator;
    protected Expression expr;
    }
