package org.xvm.compiler.ast;


import org.xvm.compiler.Token;

import org.xvm.util.ListMap;

import java.util.Map;


/**
 * A parameter type and name, with an optional default value.
 *
 * @author cp 2017.03.28
 */
public class Parameter
        extends AstNode
    {
    // ----- constructors --------------------------------------------------------------------------

    public Parameter(TypeExpression type, Token name)
        {
        this (type, name, null);
        }

    public Parameter(TypeExpression type, Token name, Expression value)
        {
        this.type  = type;
        this.name  = name;
        this.value = value;
        }


    // ----- accessors -----------------------------------------------------------------------------


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();
        sb.append(type)
          .append(' ')
          .append(name.getValue());

        if (value != null)
            {
            sb.append(" = ")
              .append(value);
            }

        return sb.toString();
        }

    public String toTypeParamString()
        {
        String s = String.valueOf(name.getValue());
        return type == null ? s : (s + " extends " + type);
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
        map.put("type", type);
        map.put("value", value);
        return map;
        }


    // ----- fields --------------------------------------------------------------------------------

    protected TypeExpression type;
    protected Token name;
    protected Expression value;
    }
