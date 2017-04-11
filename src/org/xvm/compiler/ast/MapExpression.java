package org.xvm.compiler.ast;


import java.util.List;


/**
 * A list expression is an expression containing some number (0 or more) expressions of some common
 * type.
 *
 * @author cp 2017.04.07
 */
public class MapExpression
        extends Expression
    {
    public MapExpression(TypeExpression type, List<Expression> keys, List<Expression> values)
        {
        this.type   = type;
        this.keys   = keys;
        this.values = values;
        }

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

    public final TypeExpression   type;
    public final List<Expression> keys;
    public final List<Expression> values;
    }
