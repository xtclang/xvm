package org.xvm.compiler.ast;


import org.xvm.compiler.Token;
import org.xvm.util.ListMap;

import java.util.Map;

/**
 * Generic expression for something that follows the pattern "expression operator".
 *
 * @author cp 2017.04.06
 */
public class PostfixExpression
        extends Expression
    {
    // ----- constructors --------------------------------------------------------------------------

    public PostfixExpression(Expression expr, Token operator)
        {
        this.expr     = expr;
        this.operator = operator;
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public TypeExpression toTypeExpression()
        {
        if (operator.getId() == Token.Id.COND)
            {
            // convert "expr?" to "type?"
            return new NullableTypeExpression(expr.toTypeExpression());
            }

        return super.toTypeExpression();
        }

    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append(expr)
          .append(operator.getId().TEXT);

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

    protected Expression expr;
    protected Token      operator;
    }
