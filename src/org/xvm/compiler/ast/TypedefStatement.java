package org.xvm.compiler.ast;


import org.xvm.compiler.Token;

import org.xvm.util.ListMap;

import java.util.Map;


/**
 * A typedef statement specifies a type to alias as a simple name.
 *
 * @author cp 2017.03.28
 */
public class TypedefStatement
        extends Statement
    {
    // ----- constructors --------------------------------------------------------------------------

    public TypedefStatement(Expression cond, Token alias, TypeExpression type)
        {
        this.cond  = cond;
        this.alias = alias;
        this.type  = type;
        }


    // ----- accessors -----------------------------------------------------------------------------


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        if (cond != null)
            {
            sb.append("if (")
              .append(cond)
              .append(") { ");
            }

        sb.append("typedef ")
          .append(type)
          .append(' ')
          .append(alias.getValue())
          .append(';');

        if (cond != null)
            {
            sb.append(" }");
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
        map.put("cond", cond);
        map.put("type", type);
        return map;
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Expression     cond;
    protected Token          alias;
    protected TypeExpression type;
    }
