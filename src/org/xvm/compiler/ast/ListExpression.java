package org.xvm.compiler.ast;


import java.util.List;


/**
 * A list expression is an expression containing some number (0 or more) expressions of some common
 * type.
 *
 * @author cp 2017.04.07
 */
public class ListExpression
        extends Expression
    {
    public ListExpression(TypeExpression type, List<Expression> exprs)
        {
        this.type  = type;
        this.exprs = exprs;
        }

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append('{');

        boolean first = true;
        for (Expression expr : exprs)
            {
            if (first)
                {
                first = false;
                }
            else
                {
                sb.append(", ");
                }
            sb.append(expr);
            }

        sb.append('}');

        return sb.toString();
        }

    public final TypeExpression   type;
    public final List<Expression> exprs;
    }
