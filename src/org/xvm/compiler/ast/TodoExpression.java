package org.xvm.compiler.ast;


import org.xvm.compiler.Token;

import org.xvm.util.ListMap;

import java.util.Map;

/**
 * A to-do expression raises an exception indicating missing functionality,
 * with an optional message. It can be used as an expression, as a type expression, or as a
 * statement.
 *
 * @author cp 2017.03.28
 */
public class TodoExpression
        extends TypeExpression
    {
    // ----- constructors --------------------------------------------------------------------------

    public TodoExpression(Token keyword, Expression message)
        {
        this.keyword = keyword;
        this.message = message;
        }


    // ----- accessors -----------------------------------------------------------------------------

    public boolean canComplete()
        {
        return false;
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();
        sb.append("TODO(");
        if (message != null)
            {
            sb.append(message);
            }
        sb.append(')');
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
        map.put("message", message);
        return map;
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Token      keyword;
    protected Expression message;
    }
