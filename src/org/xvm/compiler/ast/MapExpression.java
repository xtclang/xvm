package org.xvm.compiler.ast;


import com.sun.xml.internal.bind.v2.TODO;
import java.lang.reflect.Field;

import java.util.List;


/**
 * A map expression is an expression containing some number (0 or more) entries, each of which has
 * a key and a value.
 */
public class MapExpression
        extends Expression
    {
    // ----- constructors --------------------------------------------------------------------------

    public MapExpression(TypeExpression type, List<Expression> keys, List<Expression> values, long lEndPos)
        {
        this.type    = type;
        this.keys    = keys;
        this.values  = values;
        this.lEndPos = lEndPos;
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public long getStartPosition()
        {
        return type.getStartPosition();
        }

    @Override
    public long getEndPosition()
        {
        return lEndPos;
        }

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }


    // ----- compilation ---------------------------------------------------------------------------

    // TODO - implicit type, validation, code generation, etc.

    @Override
    public boolean isAborting()
        {
        for (Expression key : keys)
            {
            if (key.isAborting())
                {
                return true;
                }
            }
        for (Expression val : values)
            {
            if (val.isAborting())
                {
                return true;
                }
            }
        return false;
        }

    @Override
    public boolean isShortCircuiting()
        {
        for (Expression key : keys)
            {
            if (key.isShortCircuiting())
                {
                return true;
                }
            }
        for (Expression val : values)
            {
            if (val.isShortCircuiting())
                {
                return true;
                }
            }
        return false;
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
    protected long             lEndPos;

    private static final Field[] CHILD_FIELDS = fieldsForNames(MapExpression.class, "type", "keys", "values");
    }
