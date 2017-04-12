package org.xvm.compiler.ast;


import org.xvm.compiler.Token;
import org.xvm.util.ListMap;

import java.util.Map;


/**
 * Used for named arguments.
 *
 * @author cp 2017.04.08
 */
public class NamedExpression
        extends Expression
    {
    // ----- constructors --------------------------------------------------------------------------

    public NamedExpression(Token name, Expression expr)
        {
        this.name = name;
        this.expr = expr;
        }


    // ----- accessors -----------------------------------------------------------------------------


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        return name + " = " + expr;
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
    protected Token      name;
    }
