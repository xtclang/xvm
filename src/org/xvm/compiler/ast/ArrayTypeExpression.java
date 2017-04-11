package org.xvm.compiler.ast;


import java.util.List;

/**
 * An array type expression is a type expression followed by an array indicator.
 *
 * @author cp 2017.03.31
 */
public class ArrayTypeExpression
        extends TypeExpression
    {
    public ArrayTypeExpression(TypeExpression type, int dims)
        {
        this.type    = type;
        this.dims    = dims;
        this.indexes = null;
        }

    public ArrayTypeExpression(TypeExpression type, List<Expression> indexes)
        {
        this.type    = type;
        this.dims    = indexes.size();
        this.indexes = null;
        }

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append(type)
          .append('[');

        for (int i = 0; i < dims; ++i)
            {
            if (i > 0)
                {
                sb.append(',');
                }

            if (indexes == null)
                {
                sb.append('?');
                }
            else
                {
                sb.append(indexes.get(i));
                }
            }

          sb.append(']');

        return sb.toString();
        }

    public final TypeExpression   type;
    public final int              dims;
    public final List<Expression> indexes;
    }
