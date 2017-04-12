package org.xvm.compiler.ast;


import org.xvm.util.ListMap;

import java.util.Map;

/**
 * A ternary expression is the "a ? b : c" expression.
 *
 * @author cp 2017.04.06
 */
public class TernaryExpression
        extends Expression
    {
    // ----- constructors --------------------------------------------------------------------------

    public TernaryExpression(Expression expr, Expression exprThen, Expression exprElse)
        {
        this.expr     = expr;
        this.exprThen = exprThen;
        this.exprElse = exprElse;
        }


    // ----- accessors -----------------------------------------------------------------------------


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append(expr)
          .append(" ? ")
          .append(exprThen)
          .append(" : ")
          .append(exprElse);

        return sb.toString();
        }

    @Override
    public Map<String, Object> getDumpChildren()
        {
        ListMap<String, Object> map = new ListMap();
        map.put("expr", expr);
        map.put("exprThen", exprThen);
        map.put("exprElse", exprElse);
        return map;
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Expression expr;
    protected Expression exprThen;
    protected Expression exprElse;
    }
