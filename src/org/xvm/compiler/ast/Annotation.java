package org.xvm.compiler.ast;


import java.util.List;


/**
 * An annotation is a type annotation and an optional argument list.
 *
 * @author cp 2017.03.31
 */
public class Annotation
    {
    public Annotation(NamedTypeExpression type, List<Expression> args)
        {
        this.type = type;
        this.args = args;
        }

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append('@')
          .append(type);

        if (args != null)
            {
            sb.append('(');

            boolean first = true;
            for (Expression expr : args)
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

            sb.append(')');
            }

        return sb.toString();
        }

    public final NamedTypeExpression type;
    public final List<Expression> args;
    }
