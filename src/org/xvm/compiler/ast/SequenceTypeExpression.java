package org.xvm.compiler.ast;


import org.xvm.util.ListMap;

import java.util.Map;

/**
 * An sequence type expression is a type expression followed by an ellipsis.
 *
 * @author cp 2017.03.31
 */
public class SequenceTypeExpression
        extends TypeExpression
    {
    // ----- constructors --------------------------------------------------------------------------

    public SequenceTypeExpression(TypeExpression type)
        {
        this.type = type;
        }


    // ----- accessors -----------------------------------------------------------------------------


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append(type)
          .append("...");

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
        map.put("type", type);
        return map;
        }


    // ----- fields --------------------------------------------------------------------------------

    protected TypeExpression type;
    }
