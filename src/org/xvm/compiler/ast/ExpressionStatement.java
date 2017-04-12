package org.xvm.compiler.ast;


import org.xvm.util.ListMap;

import java.util.Map;


/**
 * An expression statement is just an expression that someone stuck a semicolon on the end of.
 *
 * @author cp 2017.04.03
 */
public class ExpressionStatement
        extends Statement
    {
    // ----- constructors --------------------------------------------------------------------------

    public ExpressionStatement(Expression expr)
        {
        this(expr, true);
        }

    public ExpressionStatement(Expression expr, boolean standalone)
        {
        this.expr = expr;
        this.term = standalone;
        }


    // ----- accessors -----------------------------------------------------------------------------


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();
        sb.append(expr);
        if (term)
            {
            sb.append(';');
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
        return map;
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Expression expr;
    protected boolean    term;
    }
