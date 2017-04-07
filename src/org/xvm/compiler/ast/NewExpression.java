package org.xvm.compiler.ast;


import org.xvm.compiler.Token;

import java.util.List;


/**
 * "New object" expression.
 *
 * @author cp 2017.04.06
 */
public class NewExpression
        extends PrefixExpression
    {
    public NewExpression(Token operator, Expression expr, List<Expression> args)
        {
        super(operator, expr);
        this.args = args;
        }

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append(operator.getId().TEXT)
          .append(' ')
          .append(expr)
          .append('(');

        boolean first = true;
        for (Expression arg : args)
            {
            if (first)
                {
                first = false;
                }
            else
                {
                sb.append(", ");
                }
            sb.append(arg);
            }
        sb.append(')');

        return sb.toString();
        }

    public final List<Expression> args;
    }
