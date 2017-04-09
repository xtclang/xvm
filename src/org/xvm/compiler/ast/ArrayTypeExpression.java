package org.xvm.compiler.ast;


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
        this.type = type;
        this.dims = dims;
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
            sb.append('?');
            }

          sb.append(']');

        return sb.toString();
        }

    public final TypeExpression type;
    public final int dims;
    }
