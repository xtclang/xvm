package org.xvm.compiler.ast;


import java.util.List;


/**
 * An array access expression is an expression followed by an array index expression.
 *
 * @author cp 2017.04.08
 */
public class ArrayAccessExpression
        extends Expression
    {
    public ArrayAccessExpression(Expression expr, List<Expression> indexes)
        {
        this.expr    = expr;
        this.indexes = indexes;
        }

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append(expr)
          .append('[');

        boolean first = true;
        for (Expression index : indexes)
            {
            if (first)
                {
                first = false;
                }
            else
                {
                sb.append(", ");
                }
            sb.append(index);
            }

          sb.append(']');

        return sb.toString();
        }

    public final Expression       expr;
    public final List<Expression> indexes;
    }
