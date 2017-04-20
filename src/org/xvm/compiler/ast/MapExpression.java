package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.List;


/**
 * A map expression is an expression containing some number (0 or more) entries, each of which has
 * a key and a value.
 *
 * @author cp 2017.04.07
 */
public class MapExpression
        extends Expression
    {
    // ----- constructors --------------------------------------------------------------------------

    public MapExpression(TypeExpression type, List<Expression> keys, List<Expression> values)
        {
        this.type   = type;
        this.keys   = keys;
        this.values = values;
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append("\n    {");

        for (int i = 0, c = keys.size(); i < c; ++i)
            {
            if (i > 0)
                {
                sb.append(',');
                }

            sb.append("\n    ")
              .append(keys.get(i))
              .append(" = ")
              .append(values.get(i));
            }

        sb.append("\n    }");

        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        return "size=" + keys.size();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected TypeExpression   type;
    protected List<Expression> keys;
    protected List<Expression> values;

    private static final Field[] CHILD_FIELDS = fieldsForNames(MapExpression.class, "type", "keys", "values");
    }
